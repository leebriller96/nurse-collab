package com.nursecollab.domain.staff.service;

import com.nursecollab.global.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 갱신 토큰을 Redis 에 둔다.
 *
 * 인증 서버(업무 쪽)만 쓴다. 원내는 토큰을 검증만 하므로 Redis 가 없어도 떠야 해서
 * global 이 아니라 여기 둔다.
 *
 * JWT 만으로는 로그아웃을 구현할 수 없다. 서버가 발급한 토큰을 회수할 방법이 없기 때문이다.
 * 계정당 유효한 갱신 토큰 하나만 저장해 두면 로그아웃(삭제)과
 * 재사용 탐지(불일치 시 거부)가 둘 다 된다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh-token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;

    public void save(Long staffId, String refreshToken) {
        redisTemplate.opsForValue()
                .set(key(staffId), refreshToken, properties.refreshTokenValidity());
    }

    public boolean matches(Long staffId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(staffId));
        return stored != null && stored.equals(refreshToken);
    }

    public void remove(Long staffId) {
        redisTemplate.delete(key(staffId));
    }

    private String key(Long staffId) {
        return KEY_PREFIX + staffId;
    }
}
