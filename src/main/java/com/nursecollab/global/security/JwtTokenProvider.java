package com.nursecollab.global.security;

import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;

/**
 * 토큰을 만들고 검증한다.
 *
 * 발급은 개인키가 있는 역할만 한다. 원내 게이트웨이처럼 검증만 하는 역할은
 * 개인키 없이 뜬다. 없는데 발급을 시도하면 조용히 넘어가지 않고 터진다 —
 * 그 역할이 토큰을 만들 수 있게 된 것 자체가 설정 사고이기 때문이다.
 */
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

    private final PrivateKey signingKey;
    private final PublicKey verifyKey;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.verifyKey = JwtKeys.readPublic(properties.publicKey());
        this.signingKey = properties.privateKey() == null
                ? null
                : JwtKeys.readPrivate(properties.privateKey());
    }

    /** 이 역할이 토큰을 발급할 수 있는가. 원내 게이트웨이는 못 한다. */
    public boolean canIssue() {
        return signingKey != null;
    }

    private PrivateKey requireSigningKey() {
        if (signingKey == null) {
            throw new IllegalStateException(
                    "이 역할에는 서명용 개인키가 없습니다. 토큰 발급은 인증 서버가 합니다.");
        }
        return signingKey;
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
                .signWith(requireSigningKey())
                .compact();
    }

    public String createRefreshToken(Staff staff) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(staff.getId()))
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.refreshTokenValidity())))
                .signWith(requireSigningKey())
                .compact();
    }

    /** 서명과 만료를 검증한다. 실패하면 io.jsonwebtoken.JwtException 계열이 던져진다. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(verifyKey)
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
