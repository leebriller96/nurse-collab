package com.nursecollab.domain.notification.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFC 8291 암호화를 부록 A 의 시험값에 바이트 단위로 맞춘다.
 *
 * 라이브러리 없이 JDK 로 짰다. 직접 짠 암호는 "브라우저가 풀었다" 는 확인만으로는 부족하다 —
 * 틀린 구현도 어느 한 브라우저에서는 우연히 통할 수 있고, 틀리면 알림이 조용히 안 뜰 뿐이라 아무도 모른다.
 * 표준 문서가 내놓은 고정점에 맞추면 그 걱정이 없다.
 */
class WebPushEncryptionTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    // RFC 8291 5장·부록 A
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void RFC_8291_부록의_시험값과_바이트까지_같다() {
        KeyPair server = EcKeys.keyPair(B64.decode(AS_PUBLIC), B64.decode(AS_PRIVATE));

        byte[] body = WebPushEncryption.encrypt(
                PLAINTEXT.getBytes(StandardCharsets.US_ASCII),
                B64.decode(UA_PUBLIC), B64.decode(AUTH_SECRET),
                server, B64.decode(SALT));

        assertThat(body).isEqualTo(B64.decode(EXPECTED_BODY));
    }

    @Test
    void 기기_개인키로_다시_풀린다() {
        // 실제 발송은 매번 새 임시키와 salt 를 쓴다. 고정값 경로만 맞고 이 경로가 틀리면 소용없다.
        byte[] body = WebPushEncryption.encrypt(
                "302호 / 뇌 MRI".getBytes(StandardCharsets.UTF_8),
                B64.decode(UA_PUBLIC), B64.decode(AUTH_SECRET));

        KeyPair device = EcKeys.keyPair(B64.decode(UA_PUBLIC), B64.decode(UA_PRIVATE));
        byte[] plain = WebPushEncryption.decrypt(body, device, B64.decode(AUTH_SECRET));

        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo("302호 / 뇌 MRI");
    }
}
