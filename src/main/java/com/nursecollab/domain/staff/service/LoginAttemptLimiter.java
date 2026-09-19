package com.nursecollab.domain.staff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 같은 아이디로 비밀번호를 거듭 틀리면 한동안 막는다.
 *
 * 실패를 감사 로그에 남기는 것과 막는 것은 다르다. 기록만 하면 대입은 계속된다.
 * 아이디 기준으로 세는 것은 한 계정을 노리는 대입을 막기 위해서다 — 대신 남의 아이디를
 * 일부러 틀려 잠글 수 있다. 창이 짧고(기본 15분) 화면에 "잠시 후" 라고만 말하므로
 * 그 손해가 대입을 열어 두는 것보다 작다고 본다.
 *
 * 인증 서버(업무 쪽)만 쓴다. 갱신 토큰과 같은 Redis 에 두어 서버가 여러 대여도 함께 센다.
 * 없는 아이디도 센다. 있는 아이디만 세면 잠기는지 아닌지로 계정 존재를 알 수 있다.
 */
@Component
public class LoginAttemptLimiter {

    private static final String KEY_PREFIX = "login-fail:";

    private final StringRedisTemplate redisTemplate;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(StringRedisTemplate redisTemplate,
                               @Value("${app.login-limit.max-failures:10}") int maxFailures,
                               @Value("${app.login-limit.window:15m}") Duration window) {
        this.redisTemplate = redisTemplate;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    public boolean isLocked(String loginId) {
        String count = redisTemplate.opsForValue().get(key(loginId));
        return count != null && Integer.parseInt(count) >= maxFailures;
    }

    /** 실패를 하나 더 센다. 창은 마지막 실패에서 다시 시작한다. */
    public void recordFailure(String loginId) {
        String key = key(loginId);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, window);
    }

    public void reset(String loginId) {
        redisTemplate.delete(key(loginId));
    }

    private static String key(String loginId) {
        // 대소문자를 바꿔 가며 세는 것을 피한다. 길이는 자른다 — 키 자체가 커지면 안 된다.
        String normalized = loginId == null ? "" : loginId.toLowerCase(Locale.ROOT);
        return KEY_PREFIX + (normalized.length() > 50 ? normalized.substring(0, 50) : normalized);
    }
}
