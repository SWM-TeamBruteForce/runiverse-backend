package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.DISCONNECTED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.FINISHED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.PAUSED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("러닝 화면 참가자 상태 단위 테스트")
public class LiveRunningStatusTest {

    // 행 = 지금 상태(null은 상태가 아직 없는 참가자), 열 = 바꾸려는 상태
    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of(null, RUNNING, true),
                Arguments.of(null, PAUSED, true),
                Arguments.of(null, DISCONNECTED, false),
                Arguments.of(null, FINISHED, true),

                Arguments.of(RUNNING, RUNNING, true),
                Arguments.of(RUNNING, PAUSED, true),
                Arguments.of(RUNNING, DISCONNECTED, true),
                Arguments.of(RUNNING, FINISHED, true),

                Arguments.of(PAUSED, RUNNING, true),
                Arguments.of(PAUSED, PAUSED, true),
                Arguments.of(PAUSED, DISCONNECTED, true),
                Arguments.of(PAUSED, FINISHED, true),

                Arguments.of(DISCONNECTED, RUNNING, true),
                Arguments.of(DISCONNECTED, PAUSED, true),
                Arguments.of(DISCONNECTED, DISCONNECTED, false),
                Arguments.of(DISCONNECTED, FINISHED, true),

                Arguments.of(FINISHED, RUNNING, false),
                Arguments.of(FINISHED, PAUSED, false),
                Arguments.of(FINISHED, DISCONNECTED, false),
                Arguments.of(FINISHED, FINISHED, false));
    }

    @ParameterizedTest(name = "{0} → {1} = {2}")
    @MethodSource("transitions")
    @DisplayName("전이표의 모든 칸을 따른다")
    void followsTransitionTable(LiveRunningStatus previous, LiveRunningStatus next, boolean expected) {
        // when & then
        assertThat(next.canChangeFrom(previous)).isEqualTo(expected);
    }

    @Test
    @DisplayName("전이표가 모든 상태 조합을 덮는다")
    void transitionTableCoversEveryPair() {
        // given -> 상태를 늘리고 표를 안 늘리면 새 상태의 전이가 검증 없이 빠진다
        int previousCount = LiveRunningStatus.values().length + 1;   // null 포함
        int nextCount = LiveRunningStatus.values().length;

        // when & then
        assertThat(transitions().count()).isEqualTo((long) previousCount * nextCount);
    }

    @ParameterizedTest
    @EnumSource(LiveRunningStatus.class)
    @DisplayName("FINISHED에서는 어디로도 갈 수 없다")
    void finishedIsTerminal(LiveRunningStatus next) {
        // when & then -> 종료 후 닫히는 연결이나 늦게 온 좌표가 FINISHED를 덮으면 안 된다
        assertThat(next.canChangeFrom(FINISHED)).isFalse();
    }

    @Test
    @DisplayName("끊김은 붙어 있던 참가자에게만 남는다")
    void disconnectedOnlyFromConnected() {
        // when & then -> 한 번도 붙지 않았거나 이미 끊긴 참가자에게는 다시 쓰지 않는다
        assertThat(Arrays.stream(LiveRunningStatus.values())
                .filter(DISCONNECTED::canChangeFrom))
                .containsExactlyInAnyOrder(RUNNING, PAUSED);
        assertThat(DISCONNECTED.canChangeFrom(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = LiveRunningStatus.class, names = {"RUNNING", "PAUSED"})
    @DisplayName("끊긴 상태는 START·RESUME·PAUSE·새 좌표로 풀린다")
    void disconnectedRecoversByAnyClientMessage(LiveRunningStatus next) {
        // when & then -> 재연결 뒤 옛 연결의 끊김이 늦게 반영돼도 다음 메시지가 바로잡는다
        assertThat(next.canChangeFrom(DISCONNECTED)).isTrue();
    }
}
