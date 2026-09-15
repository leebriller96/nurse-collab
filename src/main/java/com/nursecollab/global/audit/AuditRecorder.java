package com.nursecollab.global.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 감사 기록을 실제로 적는다.
 *
 * 별도 빈으로 둔 이유: 같은 클래스 안에서 부르면 프록시를 타지 않아
 * @Transactional 이 아무 일도 하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditLogRepository auditLogRepository;

    /**
     * 조회 요청은 읽기 전용 트랜잭션 안에서 끝나므로 거기에 끼워 넣으면 쓰기가 막힌다.
     * 본 요청이 롤백되더라도 "시도했다" 는 사실은 남는 것이 맞다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }

    /**
     * 지금 요청의 접속 주소와 브라우저를 붙여 남긴다.
     *
     * 로그인 실패처럼 예외로 끝나는 요청도 남아야 해서 본 트랜잭션과 따로 커밋한다.
     * 적재가 실패하면 예외를 그대로 올린다. 삼킬지는 부르는 쪽이 정한다 —
     * 여기서 삼키면 REQUIRES_NEW 트랜잭션이 롤백 표시된 채 커밋을 시도해 엉뚱한 예외가 난다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequest(Long actorId, String action, String targetType, Long targetId,
                              Map<String, Object> detail) {
        HttpServletRequest request = currentRequest();
        auditLogRepository.save(AuditLog.of(
                actorId, action, targetType, targetId,
                // 환자 칸은 채우지 않는다. 환자에 관한 기록은 원내가 남긴다.
                null,
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : request.getHeader("User-Agent"),
                detail));
    }

    static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
                ? servlet.getRequest() : null;
    }
}
