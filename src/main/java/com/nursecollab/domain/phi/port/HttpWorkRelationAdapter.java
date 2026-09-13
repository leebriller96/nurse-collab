package com.nursecollab.domain.phi.port;

import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 원내가 업무 서버에 HTTP 로 묻는다.
 *
 * <p><b>사용자의 토큰을 그대로 싣는다.</b> 원내 서버 자신의 자격증명을 두면
 * 그것 하나로 모든 파트에 대해 물을 수 있다. 사용자 토큰이면 업무 서버가 소속을 대조해
 * 그 사람이 물을 수 있는 만큼만 답한다.
 *
 * <p><b>닿지 못하면 "관계 없음" 으로 치지 않는다.</b> 그러면 검사실에서 방금 접수한 환자의
 * 주의사항이 "권한 없음" 으로 보여 끊긴 줄 모른다. 따로 {@code PHI-002} 를 던진다.
 *
 * <p>요청·응답 모양을 업무 쪽 DTO 에서 가져오지 않고 여기 레코드로 따로 둔다.
 * 원내 코드는 업무 쪽 패키지를 부르지 않는다(PhiBoundaryTest). 두 모양이 어긋나지 않는지는
 * WorkRelationContractTest 가 실제 HTTP 로 확인한다.
 */
@Slf4j
@Component
// @ConditionalOnProperty 는 빈 문자열도 "있음" 으로 친다. 비워 두면 빈 주소로 뜬다.
@ConditionalOnExpression("!'${app.work-api.base-url:}'.isEmpty()")
public class HttpWorkRelationAdapter implements WorkRelationPort {

    private final RestClient client;

    public HttpWorkRelationAdapter(
            @Value("${app.work-api.base-url}") String baseUrl,
            @Value("${app.work-api.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.work-api.read-timeout:3s}") Duration readTimeout) {
        // 기다리는 동안 간호사는 화면 앞에 서 있다. 짧게 끊고 끊겼다고 말하는 편이 낫다.
        HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public boolean hasActiveOrderTo(UUID subjectRef, Long departmentId) {
        return subjectsWithActiveOrdersTo(List.of(subjectRef), departmentId).contains(subjectRef);
    }

    @Override
    public Set<UUID> subjectsWithActiveOrdersTo(Collection<UUID> subjectRefs, Long departmentId) {
        if (subjectRefs == null || subjectRefs.isEmpty()) return Set.of();

        String token = bearer();
        ActiveSubjects answer = call(() -> client.post()
                .uri("/api/v1/work-relations/active-subjects")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActiveSubjectsQuery(departmentId, List.copyOf(subjectRefs)))
                .retrieve()
                .body(ActiveSubjects.class));

        return answer == null || answer.subjectRefs() == null
                ? Set.of() : Set.copyOf(answer.subjectRefs());
    }

    @Override
    public void registerEpisode(UUID subjectRef, Long departmentId, String roomNo, String bedNo,
                                OffsetDateTime admittedAt) {
        String token = bearer();
        call(() -> client.post()
                .uri("/api/v1/work-relations/episodes")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                // 진단명과 거동 여부는 넘기지 않는다
                .body(new EpisodeRegistration(subjectRef, departmentId, roomNo, bedNo, admittedAt))
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
            }
            // 그 밖의 4xx 는 두 쪽 계약이 어긋났다는 뜻이다. 사용자 잘못이 아니므로 크게 남긴다.
            log.error("업무 서버가 원내의 질문을 거절했습니다. 계약이 어긋났는지 확인하세요. status={}",
                    e.getStatusCode(), e);
            throw new BusinessException(ErrorCode.WORK_RELATION_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("업무 서버에 닿지 못했습니다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WORK_RELATION_UNAVAILABLE);
        }
    }

    /**
     * 지금 요청의 토큰. 사용자 요청 밖(예약 작업 등)에서 부르면 실을 토큰이 없다.
     * 그때 서버용 자격증명으로 대신하지 않고 터뜨린다 — 그 대체가 곧 이 설계가 막으려는 구멍이다.
     */
    private static String bearer() {
        HttpServletRequest request =
                RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                        ? attrs.getRequest() : null;
        String header = request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            throw new IllegalStateException(
                    "업무 서버에 물으려면 사용자 요청의 토큰이 필요합니다. 요청 밖에서 부르지 마세요.");
        }
        return header;
    }

    public record ActiveSubjectsQuery(Long departmentId, List<UUID> subjectRefs) {}

    public record ActiveSubjects(List<UUID> subjectRefs) {}

    public record EpisodeRegistration(UUID subjectRef, Long departmentId, String roomNo,
                                      String bedNo, OffsetDateTime admittedAt) {}
}
