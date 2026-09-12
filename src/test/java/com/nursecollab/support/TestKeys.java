package com.nursecollab.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 테스트용 RSA 키.
 *
 * 저장소에 든 개발용 키쌍을 그대로 쓰거나, 다른 키로 서명된 토큰을 만들어야 할 때
 * 즉석에서 키를 뽑는다. PEM 을 만드는 코드가 테스트마다 흩어지면
 * 한 곳만 고쳐지고 왜 안 되는지 한참 찾게 된다.
 */
public final class TestKeys {

    private TestKeys() {}

    public static Resource devPrivate() {
        return new ClassPathResource("keys/local-dev-only-private.pem");
    }

    public static Resource devPublic() {
        return new ClassPathResource("keys/local-dev-only-public.pem");
    }

    /** 새 키쌍을 만들어 [개인키, 공개키] PEM 파일로 준다 */
    public static Resource[] freshPair() {
        KeyPair pair = generate();
        return new Resource[]{
                pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()),
        };
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Resource pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(der);
        String text = "-----BEGIN %s-----%n%s%n-----END %s-----%n".formatted(label, body, label);
        try {
            Path file = Files.createTempFile("jwt-test-", ".pem");
            file.toFile().deleteOnExit();
            Files.writeString(file, text);
            return new FileSystemResource(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
