package com.nursecollab.global.security;

import com.nursecollab.support.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영에 개발용 서명 키가 올라가는 것을 막는 검사.
 *
 * 이 검사가 없으면 키를 빠뜨린 채 배포해도 앱이 멀쩡히 뜬다.
 * 그리고 저장소에 공개된 키로 서명하므로 누구나 관리자 토큰을 만들 수 있다.
 */
class JwtKeyGuardTest {

    private static final Resource DEV_PUBLIC = TestKeys.devPublic();

    @Test
    void 저장소에_든_개발용_키는_거부한다() {
        assertThatThrownBy(() -> JwtKeyGuard.validate(DEV_PUBLIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("개발용 기본 서명 키");
    }

    @Test
    void 새로_만든_키는_통과한다() {
        assertThatCode(() -> JwtKeyGuard.validate(TestKeys.freshPair()[1]))
                .doesNotThrowAnyException();
    }

    @Test
    void 키_파일이_없으면_거부한다() {
        assertThatThrownBy(() -> JwtKeyGuard.validate(new ClassPathResource("keys/nope.pem")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void 적어_둔_지문이_실제_개발용_키와_같다() {
        // 개발용 키를 새로 만들면서 이 상수를 안 고치면 방어가 그대로 무력해진다.
        // 아무 일도 일어나지 않으므로 아무도 모른다. 그래서 여기서 붙들어 둔다.
        assertThat(JwtKeys.fingerprint(JwtKeys.readPublic(DEV_PUBLIC)))
                .isEqualTo(JwtKeyGuard.DEV_PUBLIC_KEY_FINGERPRINT);
    }
}
