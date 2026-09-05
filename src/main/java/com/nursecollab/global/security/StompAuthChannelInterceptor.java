package com.nursecollab.global.security;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP 연결 인증.
 *
 * 브라우저의 WebSocket 은 핸드셰이크에 임의 헤더를 넣을 수 없다.
 * 그래서 토큰은 CONNECT 프레임의 네이티브 헤더로 받는다.
 * 쿼리스트링에 실으면 접속 로그와 프록시 로그에 토큰이 그대로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        LoginStaff loginStaff = resolve(accessor.getFirstNativeHeader(HEADER));
        if (loginStaff == null) {
            // 인증되지 않은 연결은 principal 없이 통과시킨다.
            // 구독 시점에 소속 파트를 확인할 수 없으므로 아무 채널도 받지 못한다.
            log.debug("인증 정보 없는 STOMP 연결 시도");
            return message;
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                loginStaff, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + loginStaff.role().name()))));
        return message;
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
