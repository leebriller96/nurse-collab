package com.nursecollab.global.security;

import com.nursecollab.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "jwtErrorCode";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = tokenProvider.parse(token);

                // 갱신 토큰으로는 API 를 호출할 수 없다. 탈취 시 피해 범위를 줄이기 위해서다.
                if (tokenProvider.isAccessToken(claims)) {
                    authenticate(tokenProvider.toLoginStaff(claims));
                } else {
                    request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
                }
            } catch (ExpiredJwtException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
            } catch (JwtException | IllegalArgumentException e) {
                // 위조·손상된 토큰. 구체적인 원인은 알려주지 않는다.
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
            }
        }

        chain.doFilter(request, response);
    }

    private void authenticate(LoginStaff loginStaff) {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + loginStaff.role().name()));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginStaff, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
