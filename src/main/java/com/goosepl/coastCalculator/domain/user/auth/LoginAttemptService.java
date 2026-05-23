package com.goosepl.coastCalculator.domain.user.auth;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 실패 횟수 카운터. 메모리 기반(ConcurrentHashMap) — 재시작 시 초기화.
 *
 * 정책:
 *  - 같은 username으로 {@value #MAX_ATTEMPTS}회 연속 실패 시 잠금
 *  - 잠금은 마지막 실패 시각으로부터 {@value #LOCK_MINUTES}분 후 자동 해제
 *  - 한 번 로그인 성공하면 카운터 리셋
 *
 * 시각 의존성은 {@link Clock}으로 분리 — 테스트에서 시간 진행을 시뮬레이션 가능.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(LOCK_MINUTES);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    /**
     * 테스트용 생성자. 운영에서는 기본 생성자(시스템 시계)를 사용.
     */
    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public void recordFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        attempts.compute(username, (k, prev) -> {
            int count = (prev == null) ? 1 : prev.count + 1;
            return new Attempt(count, Instant.now(clock));
        });
    }

    public void recordSuccess(String username) {
        if (username == null) {
            return;
        }
        attempts.remove(username);
    }

    /**
     * 해당 username이 현재 잠겨 있는지 확인. 잠금 시간이 지났으면 자동 해제하고 false 반환.
     */
    public boolean isBlocked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Attempt a = attempts.get(username);
        if (a == null) {
            return false;
        }
        if (a.count < MAX_ATTEMPTS) {
            return false;
        }
        // 잠금 만료 — 자동 해제
        if (Duration.between(a.lastFailureAt, Instant.now(clock)).compareTo(LOCK_DURATION) >= 0) {
            attempts.remove(username);
            return false;
        }
        return true;
    }

    public int maxAttempts() {
        return MAX_ATTEMPTS;
    }

    public int lockMinutes() {
        return LOCK_MINUTES;
    }

    private record Attempt(int count, Instant lastFailureAt) {}
}
