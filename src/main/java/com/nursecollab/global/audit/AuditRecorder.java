package com.nursecollab.global.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     * 본 요청이 롤백되더라도 "열어봤다" 는 사실은 남는 것이 맞다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }
}
