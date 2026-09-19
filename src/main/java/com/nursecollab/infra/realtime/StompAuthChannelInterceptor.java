package com.nursecollab.infra.realtime;

import com.nursecollab.global.security.JwtTokenProvider;
import com.nursecollab.global.security.LoginStaff;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP 연결·구독 인증.
 *
 * 브라우저의 WebSocket 은 핸드셰이크에 임의 헤더를 넣을 수 없다.
 * 그래서 토큰은 CONNECT 프레임의 네이티브 헤더로 받는다.
 * 쿼리스트링에 실으면 접속 로그와 프록시 로그에 토큰이 그대로 남는다.
 *
 * <b>구독도 여기서 막는다.</b> 파트 채널에는 요청번호·병실·가명·행위자 이름이 실린다.
 * CONNECT 만 보고 SUBSCRIBE 를 그냥 통과시키면 토큰 없이 붙어 아무 파트 채널이나
 * 구독할 수 있다 — 한동안 실제로 그랬다. 이름을 방송에 싣지 않은 것은 원내망 밖의
 * 브라우저 때문이었지, 로그인하지 않은 사람에게 보여 주려던 것이 아니다.
 *
 * 거절은 예외로 한다. 그러면 ERROR 프레임이 나가고 세션이 닫힌다. 조용히 버리면
 * 화면은 "연결됨" 인 채로 아무것도 받지 못하고, 토큰이 만료돼 다시 붙는 쪽도
 * 왜 안 오는지 알 길이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String DEPARTMENT_TOPIC = RealtimeNotifier.DEPARTMENT_TOPIC;

    private final JwtTokenProvider tokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> connect(accessor);
            case SUBSCRIBE -> subscribe(accessor);
            default -> { /* 나머지 프레임은 이미 인증된 세션 안에서만 온다 */ }
        }
        return message;
    }

    /**
     * 토큰이 없거나 만료된 연결은 받지 않는다.
     * 주체 없는 세션은 어차피 아무 채널도 구독할 수 없다. 그런 세션을 열어 두면
     * 만료된 토큰으로 다시 붙은 화면이 "연결됨" 만 보고 갱신을 기다리게 된다.
     */
    private void connect(StompHeaderAccessor accessor) {
        LoginStaff loginStaff = resolve(accessor.getFirstNativeHeader(HEADER));
        if (loginStaff == null) {
            log.debug("인증 정보 없는 STOMP 연결 시도");
            throw new AccessDeniedException("인증이 필요합니다.");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                loginStaff, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + loginStaff.role().name()))));
    }

    /**
     * 자기 파트 채널만 구독할 수 있다. 다른 목적지는 아예 없다.
     * 토큰의 소속을 믿는다 — 소속이 바뀌면 접근 토큰이 만료될 때까지 옛 파트 채널을 받는다.
     * REST 쪽이 토큰의 소속을 쓰는 것과 같은 정도의 지연이다.
     */
    private void subscribe(StompHeaderAccessor accessor) {
        LoginStaff loginStaff = principalOf(accessor.getUser());
        String destination = accessor.getDestination();
        if (loginStaff == null || destination == null
                || !destination.equals(DEPARTMENT_TOPIC + loginStaff.departmentId())) {
            log.warn("허용되지 않은 STOMP 구독 시도. staffId={}, destination={}",
                    loginStaff == null ? null : loginStaff.staffId(), destination);
            throw new AccessDeniedException("구독할 수 없는 채널입니다.");
        }
    }

    private static LoginStaff principalOf(Principal user) {
        if (user instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof LoginStaff loginStaff) {
            return loginStaff;
        }
        return null;
    }

    private LoginStaff resolve(String header) {
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        try {
            Claims claims = tokenProvider.parse(header.substring(PREFIX.length()).trim());
            return tokenProvider.isAccessToken(claims) ? tokenProvider.toLoginStaff(claims) : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
