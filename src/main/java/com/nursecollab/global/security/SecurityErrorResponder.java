package com.nursecollab.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 필터 단계에서 걸린 인증·인가 실패도 컨트롤러 예외와 같은 본문 형식으로 내려준다.
 * GlobalExceptionHandler 는 필터에서 난 예외를 잡지 못하므로 여기서 직접 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 인증 자체가 안 된 경우 (토큰 없음/만료/위조) */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object attribute = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        ErrorCode errorCode = (attribute instanceof ErrorCode code) ? code : ErrorCode.TOKEN_EXPIRED;
        write(request, response, errorCode);
    }

    /** 인증은 됐지만 역할이 모자란 경우 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, ErrorCode.INSUFFICIENT_ROLE);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}
