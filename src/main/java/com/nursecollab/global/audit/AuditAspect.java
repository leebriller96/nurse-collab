package com.nursecollab.global.audit;

import com.nursecollab.global.security.LoginStaff;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * @Audited 가 붙은 요청을 감사 로그로 남긴다.
 *
 * 나중에 붙이려면 전 코드를 뒤져야 하므로 AOP 로 자동 적재한다.
 * 컨트롤러마다 로그를 적는 코드를 넣으면 언젠가 빠뜨린 곳이 생긴다.
 *
 * <p>요청 본문은 적지 않는다. 직원 생성 요청에는 초기 비밀번호가 들어 있다.
 * 무엇이 바뀌었는지까지 남겨야 하는 곳(직원 수정)은 서비스가 직접 전후를 적는다.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditRecorder auditRecorder;

    public AuditAspect(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();

        // 실패한 요청은 남기지 않는다. 예외가 나면 proceed 에서 이미 빠져나간다.
        try {
            Long targetId = targetId(joinPoint, audited.targetIdParam());
            // 만들기 요청에는 경로에 id 가 없다. 만든 결과에서 꺼낸다.
            if (targetId == null) targetId = idOf(result);

            auditRecorder.recordRequest(currentStaffId(), audited.action(), audited.targetType(),
                    targetId, requestDetail());
        } catch (RuntimeException e) {
            // 감사 기록 실패로 요청 자체를 막지는 않는다. 대신 반드시 눈에 띄게 남긴다.
            log.error("감사 로그 적재 실패. action={}, targetType={}",
                    audited.action(), audited.targetType(), e);
        }
        return result;
    }

    private Long targetId(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(paramName) && args[i] instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }

    /** 응답 레코드의 id. 응답 DTO 는 전부 record 라 이름으로 찾는다. */
    private static Long idOf(Object result) {
        Object body = result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
        if (body == null || !body.getClass().isRecord()) return null;

        for (RecordComponent component : body.getClass().getRecordComponents()) {
            if (!component.getName().equals("id")) continue;
            try {
                return component.getAccessor().invoke(body) instanceof Number number
                        ? number.longValue() : null;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }

    private Long currentStaffId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null && authentication.getPrincipal() instanceof LoginStaff staff)
                ? staff.staffId() : null;
    }

    private Map<String, Object> requestDetail() {
        HttpServletRequest request = AuditRecorder.currentRequest();
        if (request == null) {
            return null;
        }
        return Map.of("method", request.getMethod(), "uri", request.getRequestURI());
    }
}
