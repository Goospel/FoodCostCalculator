package com.goosepl.coastCalculator.domain.user.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1-3에서 도입한 로그인 실패 잠금 정책의 자동 회귀 방지 테스트.
 * - 5회 실패 → 잠금
 * - 15분 경과 → 자동 해제
 * - 성공 시 카운터 리셋
 */
class LoginAttemptServiceTest {

    /** 테스트에서 시간을 임의로 진행시킬 수 있는 가변 Clock. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(t -> t.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new LoginAttemptService(clock);
    }

    @Nested
    @DisplayName("기본 동작")
    class BasicBehavior {

        @Test
        @DisplayName("실패 기록 전에는 차단되지 않는다")
        void initiallyNotBlocked() {
            assertThat(service.isBlocked("alice")).isFalse();
        }

        @Test
        @DisplayName("4회 연속 실패까지는 차단되지 않는다 (임계값 미만)")
        void belowThresholdNotBlocked() {
            for (int i = 0; i < 4; i++) {
                service.recordFailure("alice");
            }
            assertThat(service.isBlocked("alice")).isFalse();
        }

        @Test
        @DisplayName("5회 연속 실패 후에는 차단된다 (임계값 도달)")
        void atThresholdBlocked() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("alice");
            }
            assertThat(service.isBlocked("alice")).isTrue();
        }

        @Test
        @DisplayName("username별로 카운터가 독립적이다")
        void countersAreIndependent() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("alice");
            }
            assertThat(service.isBlocked("alice")).isTrue();
            assertThat(service.isBlocked("bob")).isFalse();
        }
    }

    @Nested
    @DisplayName("자동 해제")
    class AutoUnlock {

        @Test
        @DisplayName("잠금 후 15분 미만 경과 시 여전히 차단")
        void blockedBefore15Minutes() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("alice");
            }
            clock.advance(Duration.ofMinutes(14).plusSeconds(59));
            assertThat(service.isBlocked("alice")).isTrue();
        }

        @Test
        @DisplayName("잠금 후 정확히 15분 경과 시 자동 해제")
        void unlockedAtExactly15Minutes() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("alice");
            }
            clock.advance(Duration.ofMinutes(15));
            assertThat(service.isBlocked("alice")).isFalse();
        }

        @Test
        @DisplayName("잠금 후 15분 초과 경과 시 자동 해제")
        void unlockedAfter15Minutes() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("alice");
            }
            clock.advance(Duration.ofMinutes(20));
            assertThat(service.isBlocked("alice")).isFalse();
        }
    }

    @Nested
    @DisplayName("카운터 리셋")
    class CounterReset {

        @Test
        @DisplayName("recordSuccess 호출 시 카운터가 초기화된다")
        void successResetsCounter() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("alice");
            }
            assertThat(service.isBlocked("alice")).isTrue();

            service.recordSuccess("alice");

            assertThat(service.isBlocked("alice")).isFalse();
        }

        @Test
        @DisplayName("리셋 후 다시 실패하면 1부터 카운팅 (잠금까지 5회 더 필요)")
        void afterResetNeedsFullFiveAttempts() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("alice");
            }
            service.recordSuccess("alice");

            // 4회까지는 아직 잠기지 않음
            for (int i = 0; i < 4; i++) {
                service.recordFailure("alice");
            }
            assertThat(service.isBlocked("alice")).isFalse();

            // 5회 추가 실패 시 잠김
            service.recordFailure("alice");
            assertThat(service.isBlocked("alice")).isTrue();
        }
    }

    @Nested
    @DisplayName("방어적 코드")
    class DefensiveBehavior {

        @Test
        @DisplayName("null username은 무시된다 (실패/성공/조회 모두)")
        void nullUsernameIgnored() {
            service.recordFailure(null);
            service.recordSuccess(null);
            assertThat(service.isBlocked(null)).isFalse();
        }

        @Test
        @DisplayName("blank username은 무시된다")
        void blankUsernameIgnored() {
            service.recordFailure("");
            service.recordFailure("   ");
            assertThat(service.isBlocked("")).isFalse();
            assertThat(service.isBlocked("   ")).isFalse();
        }
    }
}
