package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.combo.RunningComboPresence;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("러닝 콤보 참가자 상태 단위 테스트")
class RunningComboPresenceTest {

    private static final UserId USER = new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1"));
    private static final Instant NOW = Instant.parse("2026-09-11T12:00:10Z");
    private static final Duration FRESHNESS = Duration.ofSeconds(19);

    private static RunningComboPresence presenceOf(Instant movedAt, Instant observedAt, boolean paused) {
        return presenceOf(movedAt, observedAt, paused, false);
    }

    private static RunningComboPresence presenceOf(
            Instant movedAt, Instant observedAt, boolean paused, boolean finished) {
        return RunningComboPresence.of(
                new RunningComboSnapshot(USER, 1_000, NOW, 0, movedAt, observedAt, paused, finished),
                NOW, FRESHNESS);
    }

    @Test
    @DisplayName("의심점이 문턱 안에 옮겨졌으면 달리는 중이다")
    void moving() {
        assertThat(presenceOf(NOW.minusSeconds(10), NOW, false)).isEqualTo(RunningComboPresence.MOVING);
    }

    @Test
    @DisplayName("문턱과 같은 시각까지는 달리는 중으로 본다")
    void movingAtExactFreshness() {
        assertThat(presenceOf(NOW.minus(FRESHNESS), NOW, false)).isEqualTo(RunningComboPresence.MOVING);
    }

    @Test
    @DisplayName("관측은 있는데 의심점이 문턱보다 오래 그대로면 서 있음이다")
    void standing() {
        assertThat(presenceOf(NOW.minusSeconds(20), NOW, false)).isEqualTo(RunningComboPresence.STANDING);
    }

    @Test
    @DisplayName("관측은 있는데 의심점이 옮겨진 적 없으면 서 있음이다 — 출발선")
    void standingAtStartLine() {
        assertThat(presenceOf(null, NOW, false)).isEqualTo(RunningComboPresence.STANDING);
    }

    @Test
    @DisplayName("관측이 문턱보다 오래 없으면 관측 없음이다 — 의심점 시각과 무관하다")
    void unobserved() {
        assertThat(presenceOf(NOW.minusSeconds(25), NOW.minusSeconds(25), false))
                .isEqualTo(RunningComboPresence.UNOBSERVED);
    }

    @Test
    @DisplayName("관측한 적이 없으면 관측 없음이다 — RUNNING_START 직후")
    void unobservedBeforeFirstObservation() {
        assertThat(presenceOf(null, null, false)).isEqualTo(RunningComboPresence.UNOBSERVED);
    }

    @Test
    @DisplayName("일시정지 표시가 있으면 다른 시각과 무관하게 일시정지다")
    void pausedWinsOverEverything() {
        assertThat(presenceOf(NOW, NOW, true)).isEqualTo(RunningComboPresence.PAUSED);
    }

    @Test
    @DisplayName("종료 표시가 있으면 방금 관측됐어도, 일시정지 표시가 함께 있어도 종료다")
    void finishedWinsOverEverything() {
        assertThat(presenceOf(NOW, NOW, false, true)).isEqualTo(RunningComboPresence.FINISHED);
        assertThat(presenceOf(NOW, NOW, true, true)).isEqualTo(RunningComboPresence.FINISHED);
    }
}
