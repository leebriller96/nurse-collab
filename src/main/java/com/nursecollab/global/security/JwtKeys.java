package com.nursecollab.global.security;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * PEM 파일에서 키를 읽는다.
 *
 * 파일로 받는 이유: 운영에서 개인키를 환경변수로 넘기면 `docker inspect`,
 * 프로세스 목록, 크래시 덤프에 그대로 찍힌다. 파일은 권한으로 막을 수 있다.
 */
final class JwtKeys {

    private JwtKeys() {}

    static PrivateKey readPrivate(Resource resource) {
        byte[] der = der(resource, "PRIVATE KEY");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "개인키를 읽지 못했습니다: " + describe(resource)
                            + " — PKCS#8 형식이어야 합니다 (-----BEGIN PRIVATE KEY-----)", e);
        }
    }

    static PublicKey readPublic(Resource resource) {
        byte[] der = der(resource, "PUBLIC KEY");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "공개키를 읽지 못했습니다: " + describe(resource), e);
        }
    }

    /**
     * 공개키의 지문.
     *
     * 저장소에 들어 있는 개발용 키인지 가려내는 데 쓴다. 키 내용을 통째로 비교하지 않는 이유는
     * 줄바꿈이나 꼬리 공백 하나로 검사가 조용히 빗나가기 때문이다.
     */
    static String fingerprint(PublicKey key) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] der(Resource resource, String label) {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("키 파일을 찾을 수 없습니다: " + describe(resource));
        }
        String pem;
        try (var in = resource.getInputStream()) {
            pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String body = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        if (body.isEmpty()) {
            throw new IllegalStateException(
                    "%s 안에 %s 블록이 없습니다".formatted(describe(resource), label));
        }
        return Base64.getDecoder().decode(body);
    }

    private static String describe(Resource resource) {
        return resource == null ? "(지정되지 않음)" : resource.getDescription();
    }
}
