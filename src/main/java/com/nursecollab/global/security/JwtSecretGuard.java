package com.nursecollab.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 운영에서 개발용 서명 키로 뜨는 것을 막는다.
 *
 * `application.yml` 의 기본값은 공개 저장소에 그대로 들어 있다.
 * 그 키로 뜨면 누구나 관리자 토큰을 위조할 수 있다. 로그인조차 필요 없다.
 * 화면은 멀쩡히 돌기 때문에 아무도 눈치채지 못한다.
 *
 * 기동을 실패시키는 편이 낫다. 뜨지 않으면 반드시 알아차리기 때문이다.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class JwtSecretGuard implements InitializingBean {

    /** 개발용 기본값에 붙여 둔 표식. application.yml 의 값과 맞춰야 한다. */
    static final String DEV_MARKER = "local-dev-only";

    /** HS256 은 256비트 이상을 요구한다. 짧으면 첫 로그인에서야 터진다. */
    static final int MIN_BYTES = 32;

    private final JwtProperties properties;

    @Override
    public void afterPropertiesSet() {
        validate(properties.secret());
    }

    static void validate(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 이 비어 있습니다. 환경변수로 지정해 주세요.");
        }
        if (secret.contains(DEV_MARKER)) {
            throw new IllegalStateException("""
                    개발용 기본 서명 키로 운영에 띄우려 했습니다.
                    이 키는 저장소에 공개돼 있어 누구나 관리자 토큰을 만들 수 있습니다.
                    .env 의 JWT_SECRET 을 채워 주세요:  openssl rand -base64 48""");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 이 너무 짧습니다(%d바이트). %d바이트 이상이어야 합니다."
                            .formatted(bytes, MIN_BYTES));
        }
    }
}
