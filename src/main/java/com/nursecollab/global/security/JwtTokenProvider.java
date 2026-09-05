package com.nursecollab.global.security;

import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_LOGIN_ID = "loginId";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_DEPARTMENT_ID = "deptId";
    private static final String CLAIM_DEPARTMENT_TYPE = "deptType";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Staff staff) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(staff.getId()))
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_LOGIN_ID, staff.getLoginId())
                .claim(CLAIM_NAME, staff.getName())
                .claim(CLAIM_ROLE, staff.getRole().name())
                .claim(CLAIM_DEPARTMENT_ID, staff.getDepartment().getId())
                .claim(CLAIM_DEPARTMENT_TYPE, staff.getDepartment().getDeptType().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenValidity())))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Staff staff) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(staff.getId()))
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.refreshTokenValidity())))
                .signWith(key)
                .compact();
    }

    /** 서명과 만료를 검증한다. 실패하면 io.jsonwebtoken.JwtException 계열이 던져진다. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public Long staffIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public LoginStaff toLoginStaff(Claims claims) {
        return new LoginStaff(
                staffIdOf(claims),
                claims.get(CLAIM_LOGIN_ID, String.class),
                claims.get(CLAIM_NAME, String.class),
                StaffRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
                claims.get(CLAIM_DEPARTMENT_ID, Number.class).longValue(),
                DeptType.valueOf(claims.get(CLAIM_DEPARTMENT_TYPE, String.class)));
    }
}
