package com.nursecollab.domain.phi.service;

import com.nursecollab.domain.phi.entity.PhiAccessLog;
import com.nursecollab.domain.phi.repository.PhiAccessLogRepository;
import com.nursecollab.global.security.LoginStaff;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 원내 접근 기록을 실제로 적는다.
 *
 * 별도 빈으로 둔 이유가 둘이다.
 * <ul>
 *   <li>같은 클래스 안에서 부르면 프록시를 타지 않아 {@code @Transactional} 이 아무 일도 하지 않는다.</li>
 *   <li><b>거절은 예외를 던지므로 본 트랜잭션이 롤백된다.</b> 거기에 끼워 넣으면
 *       거절 기록이 함께 사라진다. 제일 남겨야 할 기록이 제일 먼저 없어지는 셈이다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhiAccessRecorder {

    private final PhiAccessLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void granted(LoginStaff staff, UUID subjectRef, Long patientId, String action) {
        save(PhiAccessLog.granted(staff.staffId(), staff.loginId(), staff.departmentId(),
                subjectRef, patientId, action, ip(), userAgent()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(LoginStaff staff, UUID subjectRef, String action, String reason) {
        save(PhiAccessLog.denied(staff.staffId(), staff.loginId(), staff.departmentId(),
                subjectRef, action, reason, ip(), userAgent()));
    }

    /**
     * 기록 실패로 조회 자체를 막지는 않는다. 진료가 기록 때문에 멈추면 안 된다.
     * 대신 반드시 눈에 띄게 남긴다 — 조용히 안 쌓이면 아무도 모른다.
     */
    private void save(PhiAccessLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            log.error("원내 접근 기록 적재 실패. actor={}, subject={}",
                    entry.getActorLoginId(), entry.getSubjectRef(), e);
        }
    }

    private static HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest() : null;
    }

    private static String ip() {
        HttpServletRequest request = request();
        return request == null ? null : request.getRemoteAddr();
    }

    private static String userAgent() {
        HttpServletRequest request = request();
        return request == null ? null : request.getHeader("User-Agent");
    }
}
