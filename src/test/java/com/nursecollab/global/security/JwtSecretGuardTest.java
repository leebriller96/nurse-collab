package com.nursecollab.global.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영에 개발용 서명 키가 올라가는 것을 막는 검사.
 *
 * 이 검사가 없으면 JWT_SECRET 을 빠뜨린 채 배포해도 앱이 멀쩡히 뜬다.
 * 그리고 저장소에 공개된 키로 서명하므로 누구나 관리자 토큰을 만들 수 있다.
 */
class JwtSecretGuardTest {

    @Test
    void 개발용_기본키는_거부한다() {
        assertThatThrownBy(() -> JwtSecretGuard.validate(
                "local-dev-only-secret-please-override-in-production-0123456789"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("개발용 기본 서명 키");
    }

    @Test
    void 비어_있으면_거부한다() {
        assertThatThrownBy(() -> JwtSecretGuard.validate("  "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtSecretGuard.validate(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 짧은_키는_거부한다() {
        // HS256 은 256비트 이상을 요구한다. 짧으면 첫 로그인에서야 터진다.
        assertThatThrownBy(() -> JwtSecretGuard.validate("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("짧습니다");
    }

    @Test
    void 제대로_된_키는_통과한다() {
        assertThatCode(() -> JwtSecretGuard.validate(
                "eE9mQ2xw1t7Yv3Zk8Nb5Rr2Ss6Tt0Uu4Vv8Ww1Xx5Yy9Zz3Aa7Bb"))
                .doesNotThrowAnyException();
    }
}
