package com.nursecollab.global.audit;

import com.nursecollab.global.security.LoginStaff;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Audited 가 붙은 요청을 감사 로그로 남긴다.
 *
 * 나중에 붙이려면 전 코드를 뒤져야 하므로 AOP 로 자동 적재한다.
 * 컨트롤러마다 로그를 적는 코드를 넣으면 언젠가 빠뜨린 곳이 생긴다.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditRecorder auditRecorder;
    private final Map<String, AuditTargetResolver> resolvers;

    public AuditAspect(AuditRecorder auditRecorder,
                       List<AuditTargetResolver> resolvers) {
        this.auditRecorder = auditRecorder;
        this.resolvers = resolvers.stream()
                .collect(Collectors.toMap(AuditTargetResolver::targetType, Function.identity()));
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();

        // 실패한 요청은 남기지 않는다. 예외가 나면 proceed 에서 이미 빠져나간다.
        try {
            write(joinPoint, audited);
        } catch (RuntimeException e) {
            // 감사 기록 실패로 조회 자체를 막지는 않는다. 대신 반드시 눈에 띄게 남긴다.
            log.error("감사 로그 적재 실패. action={}, targetType={}",
                    audited.action(), audited.targetType(), e);
        }
        return result;
    }

    private void write(ProceedingJoinPoint joinPoint, Audited audited) {
        Long targetId = targetId(joinPoint, audited.targetIdParam());
        AuditTargetResolver resolver = resolvers.get(audited.targetType());
        Long patientId = (resolver == null || targetId == null)
                ? null : resolver.resolvePatientId(targetId);

        HttpServletRequest request = currentRequest();

        auditRecorder.record(AuditLog.of(
                currentStaffId(),
                audited.action(),
                audited.targetType(),
                targetId,
                patientId,
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : request.getHeader("User-Agent"),
                detailOf(request)));
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

    private Long currentStaffId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null && authentication.getPrincipal() instanceof LoginStaff staff)
                ? staff.staffId() : null;
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return (attributes instanceof ServletRequestAttributes servlet)
                ? servlet.getRequest() : null;
    }

    private Map<String, Object> detailOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return Map.of("method", request.getMethod(), "uri", request.getRequestURI());
    }
}
