package com.nursecollab.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * JWT 설정.
 *
 * 대칭키(HS256)가 아니라 키쌍(RS256)을 쓴다.
 * 대칭키면 토큰을 <b>검증</b>하려는 쪽도 <b>발급</b>할 수 있는 키를 가져야 한다.
 * 원내 게이트웨이는 검증만 하면 되는데 대칭키를 나눠 주면 관리자 토큰을 스스로 만들 수 있고,
 * 원내 쪽 키가 새면 클라우드까지 같이 뚫린다.
 *
 * @param privateKey 서명용. 토큰을 발급하는 역할만 가진다. 검증만 하는 역할에서는 비운다.
 * @param publicKey  검증용. 두 역할 모두 가진다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        Resource privateKey,
        Resource publicKey,
        Duration accessTokenValidity,
        Duration refreshTokenValidity
) {}
