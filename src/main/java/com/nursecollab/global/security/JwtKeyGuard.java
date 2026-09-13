package com.nursecollab.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 운영에서 개발용 서명 키로 뜨는 것을 막는다.
 *
 * `src/main/resources/keys/` 의 개발용 키쌍은 공개 저장소에 그대로 들어 있다.
 * 그 키로 뜨면 누구나 관리자 토큰을 위조할 수 있다. 로그인조차 필요 없다.
 * 화면은 멀쩡히 돌기 때문에 아무도 눈치채지 못한다.
 *
 * 기동을 실패시키는 편이 낫다. 뜨지 않으면 반드시 알아차리기 때문이다.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class JwtKeyGuard implements InitializingBean {

    /**
     * 저장소에 들어 있는 개발용 공개키의 SHA-256 지문.
     *
     * 파일 내용을 통째로 비교하지 않는 이유는 줄바꿈이나 꼬리 공백 하나로
     * 검사가 조용히 빗나가기 때문이다. 지문은 그런 것에 흔들리지 않는다.
     *
     * <b>keys/local-dev-only-*.pem 을 새로 만들면 이 값도 함께 바꿔야 한다.</b>
     * 안 바꾸면 이 방어가 무력해지고, 아무 일도 일어나지 않으므로 아무도 모른다.
     * JwtKeyGuardTest 가 이 값이 실제 파일과 맞는지 지켜본다.
     */
    static final String DEV_PUBLIC_KEY_FINGERPRINT =
            "650d9377bbdde6b28042ba1ade023a4d5a9e0429be0d7b136b6d47700dbce2c9";

    private final JwtProperties properties;

    @Override
    public void afterPropertiesSet() {
        validate(properties.publicKey());
    }

    static void validate(Resource publicKey) {
        String fingerprint = JwtKeys.fingerprint(JwtKeys.readPublic(publicKey));

        if (DEV_PUBLIC_KEY_FINGERPRINT.equals(fingerprint)) {
            throw new IllegalStateException("""
                    개발용 기본 서명 키로 운영에 띄우려 했습니다.
                    이 키쌍은 저장소에 공개돼 있어 누구나 관리자 토큰을 만들 수 있습니다.

                    키를 새로 만들고 파일로 넘겨 주세요:
                      openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \\
                        -out jwt-private.pem
                      openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem

                    개인키는 토큰을 발급하는 쪽에만 둡니다.
                    검증만 하는 원내 게이트웨이에는 공개키만 주면 됩니다.""");
        }
    }
}
