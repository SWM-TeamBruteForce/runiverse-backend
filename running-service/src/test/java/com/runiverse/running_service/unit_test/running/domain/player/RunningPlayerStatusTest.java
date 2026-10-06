package com.runiverse.running_service.unit_test.running.domain.player;

import com.runiverse.running_service.domain.running.player.exception.InvalidPlayerStatusTransitionException;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningPlayerStatusTest {

    // 명세로서의 전이표 — 구현과 따로 적어 둔다. 둘이 어긋나면 테스트가 깨진다.
    private static final Map<RunningPlayerStatus, Set<RunningPlayerStatus>> ALLOWED =
            new EnumMap<>(Map.of(
                    RunningPlayerStatus.INVITED,
                    EnumSet.of(RunningPlayerStatus.JOINED),
                    RunningPlayerStatus.JOINED,
                    EnumSet.of(RunningPlayerStatus.RUNNING,
                            RunningPlayerStatus.MATCHED_LEFT_PENALTY,
                            RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY),
                    RunningPlayerStatus.RUNNING,
                    EnumSet.of(RunningPlayerStatus.COMPLETED,
                            RunningPlayerStatus.RUNNING_LEFT_PENALTY,
                            RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY),
                    RunningPlayerStatus.MATCHED_LEFT_PENALTY,
                    EnumSet.noneOf(RunningPlayerStatus.class),
                    RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY,
                    EnumSet.noneOf(RunningPlayerStatus.class),
                    RunningPlayerStatus.RUNNING_LEFT_PENALTY,
                    EnumSet.noneOf(RunningPlayerStatus.class),
                    RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY,
                    EnumSet.noneOf(RunningPlayerStatus.class),
                    RunningPlayerStatus.COMPLETED,
                    EnumSet.noneOf(RunningPlayerStatus.class)));

    private static Stream<Arguments> allowedPairs() {
        return ALLOWED.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(next -> Arguments.of(entry.getKey(), next)));
    }

    private static Stream<Arguments> deniedPairs() {
        return Arrays.stream(RunningPlayerStatus.values())
                .flatMap(current -> Arrays.stream(RunningPlayerStatus.values())
                        .filter(next -> !ALLOWED.get(current).contains(next))
                        .map(next -> Arguments.of(current, next)));
    }

    @Nested
    @DisplayName("상태 전이 테스트")
    class TransitionTest {

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("com.runiverse.running_service.unit_test.running.domain.player."
                + "RunningPlayerStatusTest#allowedPairs")
        @DisplayName("전이표에 있는 조합은 다음 상태를 돌려준다")
        void transitionToAllowedStatusSuccess(RunningPlayerStatus current, RunningPlayerStatus next) {
            // when & then
            assertThat(current.transitionTo(next)).isEqualTo(next);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("com.runiverse.running_service.unit_test.running.domain.player."
                + "RunningPlayerStatusTest#deniedPairs")
        @DisplayName("전이표에 없는 조합은 예외가 발생한다")
        void transitionToDeniedStatusFails(RunningPlayerStatus current, RunningPlayerStatus next) {
            // when & then -> 이미 이탈한 참가자가 다시 달리거나 완주로 되살아나는 것을 막는다
            assertThatThrownBy(() -> current.transitionTo(next))
                    .isInstanceOf(InvalidPlayerStatusTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(RunningPlayerStatus.class)
        @DisplayName("같은 상태로는 전이할 수 없다")
        void transitionToSelfFails(RunningPlayerStatus current) {
            // when & then -> 중복 이벤트가 같은 상태를 다시 쓰지 않게 한다
            assertThatThrownBy(() -> current.transitionTo(current))
                    .isInstanceOf(InvalidPlayerStatusTransitionException.class);
        }

        @Test
        @DisplayName("초대는 수락으로만 참가로 이어진다")
        void invitedOnlyMovesToJoined() {
            // when & then -> 거절은 상태를 바꾸지 않고 deleted_at으로만 남는다
            assertThat(RunningPlayerStatus.INVITED.transitionTo(RunningPlayerStatus.JOINED))
                    .isEqualTo(RunningPlayerStatus.JOINED);
            assertThatThrownBy(() -> RunningPlayerStatus.INVITED.transitionTo(RunningPlayerStatus.RUNNING))
                    .isInstanceOf(InvalidPlayerStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("이탈 처리 테스트")
    class LeaveTest {

        @Test
        @DisplayName("대기 중 이탈은 MATCHED_LEFT_*로 간다")
        void leaveWhileJoined() {
            // when & then
            assertThat(RunningPlayerStatus.JOINED.leaveWith(true))
                    .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
            assertThat(RunningPlayerStatus.JOINED.leaveWith(false))
                    .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        }

        @Test
        @DisplayName("러닝 중 이탈은 RUNNING_LEFT_*로 간다")
        void leaveWhileRunning() {
            // when & then -> 단계를 섞으면 페널티 근거가 어긋난다
            assertThat(RunningPlayerStatus.RUNNING.leaveWith(true))
                    .isEqualTo(RunningPlayerStatus.RUNNING_LEFT_PENALTY);
            assertThat(RunningPlayerStatus.RUNNING.leaveWith(false))
                    .isEqualTo(RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY);
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class, names = {"JOINED", "RUNNING"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("대기·러닝 단계가 아니면 이탈 처리할 수 없다")
        void leaveFromOtherPhasesFails(RunningPlayerStatus current) {
            // when & then
            assertThatThrownBy(() -> current.leaveWith(true))
                    .isInstanceOf(InvalidPlayerStatusTransitionException.class);
            assertThatThrownBy(() -> current.leaveWith(false))
                    .isInstanceOf(InvalidPlayerStatusTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class, names = {"JOINED", "RUNNING"})
        @DisplayName("이탈 결과는 현재 상태에서 전이할 수 있는 값이다")
        void leaveResultIsReachableByTransition(RunningPlayerStatus current) {
            // when & then -> leaveWith와 전이표가 따로 놀면 이탈이 저장 직전에 막힌다
            assertThat(current.transitionTo(current.leaveWith(true)))
                    .isEqualTo(current.leaveWith(true));
            assertThat(current.transitionTo(current.leaveWith(false)))
                    .isEqualTo(current.leaveWith(false));
        }
    }

    @Nested
    @DisplayName("상태 분류 테스트")
    class ClassificationTest {

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class, names = {"MATCHED_LEFT_PENALTY",
                "MATCHED_LEFT_NO_PENALTY", "RUNNING_LEFT_PENALTY", "RUNNING_LEFT_NO_PENALTY"})
        @DisplayName("이탈 상태 네 개는 이탈로 분류된다")
        void leftStatuses(RunningPlayerStatus current) {
            // when & then
            assertThat(current.isLeft()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class, names = {"MATCHED_LEFT_PENALTY",
                "MATCHED_LEFT_NO_PENALTY", "RUNNING_LEFT_PENALTY", "RUNNING_LEFT_NO_PENALTY"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("그 외 상태는 이탈이 아니다")
        void nonLeftStatuses(RunningPlayerStatus current) {
            // when & then
            assertThat(current.isLeft()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class,
                names = {"MATCHED_LEFT_PENALTY", "RUNNING_LEFT_PENALTY"})
        @DisplayName("페널티 이탈만 페널티 대상이다")
        void penaltyStatuses(RunningPlayerStatus current) {
            // when & then
            assertThat(current.hasPenalty()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class,
                names = {"MATCHED_LEFT_PENALTY", "RUNNING_LEFT_PENALTY"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("면제 이탈과 정상 상태는 페널티 대상이 아니다")
        void nonPenaltyStatuses(RunningPlayerStatus current) {
            // when & then -> NO_PENALTY를 페널티로 세면 방 취소 피해자가 불이익을 받는다
            assertThat(current.hasPenalty()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class, names = {"INVITED", "JOINED", "RUNNING"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("이탈·완주는 종착 상태다")
        void terminalStatuses(RunningPlayerStatus current) {
            // when & then
            assertThat(current.isTerminal()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class, names = {"INVITED", "JOINED", "RUNNING"})
        @DisplayName("진행 중인 상태는 종착 상태가 아니다")
        void nonTerminalStatuses(RunningPlayerStatus current) {
            // when & then
            assertThat(current.isTerminal()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class,
                names = {"RUNNING", "RUNNING_LEFT_PENALTY", "RUNNING_LEFT_NO_PENALTY", "COMPLETED"})
        @DisplayName("뛰는 중·러닝 중 이탈·완주는 러닝을 시작한 상태다")
        void startedStatuses(RunningPlayerStatus current) {
            // when & then
            assertThat(current.hasStartedRunning()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = RunningPlayerStatus.class,
                names = {"RUNNING", "RUNNING_LEFT_PENALTY", "RUNNING_LEFT_NO_PENALTY", "COMPLETED"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("시작 전 상태와 시작 전 이탈은 러닝을 시작한 상태가 아니다")
        void notStartedStatuses(RunningPlayerStatus current) {
            // when & then -> 확정만 되고 안 나타난 참가자를 세면 결과 화면 인원과 어긋난다
            assertThat(current.hasStartedRunning()).isFalse();
        }
    }
}
