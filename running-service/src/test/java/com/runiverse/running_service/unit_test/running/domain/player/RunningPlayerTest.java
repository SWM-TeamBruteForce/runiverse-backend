package com.runiverse.running_service.unit_test.running.domain.player;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.exception.InvalidDesiredPlayerCountException;
import com.runiverse.running_service.domain.running.player.exception.InvalidPlayerStatusTransitionException;
import com.runiverse.running_service.domain.running.player.exception.PlayerAlreadyLeftException;
import com.runiverse.running_service.domain.running.player.exception.PlayerStartAtRequiredException;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningPlayerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 20, 6, 0);
    private static final LocalDateTime NOW = START.plusMinutes(30);
    private static final int AVG_PACE = 330;          // 5분 30초/km
    private static final int TARGET_DISTANCE = 5_000;

    // 5km / 5분30초로 낸 매칭 신청 — 각 테스트는 여기서 한 군데만 어긋뜨린다
    private static RunningPlayer request() {
        return RunningPlayer.request(UuidCreator.getTimeOrderedEpoch(), AVG_PACE, TARGET_DISTANCE, START);
    }

    // 러닝까지 들어간 참가자
    private static RunningPlayer running() {
        RunningPlayer player = request();
        player.start();
        return player;
    }

    @Nested
    @DisplayName("신청 테스트")
    class RequestTest {

        @Test
        @DisplayName("매칭 신청은 JOINED 상태로 살아 있다")
        void requestStartsAsJoined() {
            // when
            RunningPlayer player = request();

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
            assertThat(player.isActive()).isTrue();
            assertThat(player.getDeletedAt()).isEmpty();
            assertThat(player.getAvgPace().secondsPerKm()).isEqualTo(AVG_PACE);
            assertThat(player.getTargetDistance().meters()).isEqualTo(TARGET_DISTANCE);
            assertThat(player.getStartAt()).isEqualTo(START);
        }

        @Test
        @DisplayName("저장 전 신청은 식별자가 없다")
        void newPlayerHasNoId() {
            // when
            RunningPlayer player = request();

            // then
            assertThat(player.isNew()).isTrue();
            assertThat(player.getRunningPlayerId()).isEmpty();
        }

        @Test
        @DisplayName("희망 인원을 입력받지 않으면 4명으로 신청된다")
        void desiredPlayerCountDefaultsToFour() {
            // when -> 1차에는 인원 입력 UI가 없다
            RunningPlayer player = request();

            // then
            assertThat(player.getDesiredPlayerCount().value()).isEqualTo(4);
        }

        @Test
        @DisplayName("희망 인원은 정원을 넘을 수 없다")
        void desiredPlayerCountCannotExceedMax() {
            // when & then -> 저장된 값을 복원할 때도 범위를 지킨다
            assertThatThrownBy(() -> playerWithDesiredCount(5))
                    .isInstanceOf(InvalidDesiredPlayerCountException.class);
        }

        @Test
        @DisplayName("희망 인원이 1명이면 매칭이 아니라 솔로다")
        void desiredPlayerCountCannotBeOne() {
            // when & then
            assertThatThrownBy(() -> playerWithDesiredCount(1))
                    .isInstanceOf(InvalidDesiredPlayerCountException.class);
        }

        @Test
        @DisplayName("희망 시작 시각 없이는 신청할 수 없다")
        void startAtIsRequired() {
            // given
            UUID userId = UuidCreator.getTimeOrderedEpoch();

            // when & then -> 예약 매칭이라 시작 시각이 곧 매칭 조건이다
            assertThatThrownBy(() ->
                    RunningPlayer.request(userId, AVG_PACE, TARGET_DISTANCE, null))
                    .isInstanceOf(PlayerStartAtRequiredException.class);
        }

        private RunningPlayer playerWithDesiredCount(int desiredPlayerCount) {
            return RunningPlayer.builder()
                    .userId(UuidCreator.getTimeOrderedEpoch())
                    .avgPace(AVG_PACE)
                    .targetDistance(TARGET_DISTANCE)
                    .desiredPlayerCount(desiredPlayerCount)
                    .startAt(START)
                    .build();
        }
    }

    @Nested
    @DisplayName("솔로 신청 테스트")
    class RequestSoloTest {

        private static RunningPlayer requestSolo() {
            return RunningPlayer.requestSolo(UuidCreator.getTimeOrderedEpoch(), AVG_PACE, START);
        }

        @Test
        @DisplayName("솔로 신청은 목표 거리를 입력받지 않는다")
        void requestSoloHasNoTargetDistance() {
            // when
            RunningPlayer player = requestSolo();

            // then -> 끝은 유저가 정한다. NOT NULL이라 값은 들어가되 도달 불가능한 상한이다
            assertThat(player.getTargetDistance().isUnlimited()).isTrue();
        }

        @Test
        @DisplayName("솔로도 매칭과 같은 신청 row를 만든다")
        void requestSoloSharesRequestPath() {
            // when
            RunningPlayer player = requestSolo();

            // then -> 솔로도 방·플레이어 row를 만들기 때문에 신청 상태는 매칭과 같다
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
            assertThat(player.isActive()).isTrue();
            assertThat(player.isNew()).isTrue();
            assertThat(player.getAvgPace().secondsPerKm()).isEqualTo(AVG_PACE);
            assertThat(player.getStartAt()).isEqualTo(START);
        }

        @Test
        @DisplayName("솔로도 시작 시각 없이는 신청할 수 없다")
        void requestSoloRequiresStartAt() {
            // when & then -> 서버가 now()를 넣어주지만 애그리거트는 그걸 믿지 않는다
            assertThatThrownBy(() -> RunningPlayer.requestSolo(
                    UuidCreator.getTimeOrderedEpoch(), AVG_PACE, null))
                    .isInstanceOf(PlayerStartAtRequiredException.class);
        }
    }

    @Nested
    @DisplayName("이탈 테스트")
    class LeaveTest {

        @Test
        @DisplayName("확정 후 이탈은 페널티 여부가 상태로 굳는다")
        void leaveAfterMatchedWithPenalty() {
            // given
            RunningPlayer player = request();

            // when
            player.leave(true, NOW);

            // then -> 판정은 이탈 시점에 끝나고 별도 페널티 테이블이 없다
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
            assertThat(player.getStatus().hasPenalty()).isTrue();
            assertThat(player.getDeletedAt()).contains(NOW);
            assertThat(player.isActive()).isFalse();
        }

        @Test
        @DisplayName("본인 귀책이 아니면 페널티 없이 이탈한다")
        void leaveAfterMatchedWithoutPenalty() {
            // given
            RunningPlayer player = request();

            // when -> 방 취소 등으로 나가는 경우
            player.leave(false, NOW);

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
            assertThat(player.getStatus().hasPenalty()).isFalse();
            assertThat(player.getStatus().isLeft()).isTrue();
        }

        @Test
        @DisplayName("러닝 중 이탈은 RUNNING_LEFT_*로 갈린다")
        void leaveWhileRunning() {
            // given
            RunningPlayer player = running();

            // when
            player.leave(true, NOW);

            // then -> 어느 단계에서 나갔는지가 상태에 남는다
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING_LEFT_PENALTY);
            assertThat(player.getDeletedAt()).contains(NOW);
        }

        @Test
        @DisplayName("이미 끝난 신청은 다시 이탈하지 못한다")
        void cannotLeaveTwice() {
            // given
            RunningPlayer player = request();
            player.leave(true, NOW);

            // when & then -> deleted_at은 한 번 찍히면 바뀌지 않는다
            assertThatThrownBy(() -> player.leave(false, NOW.plusMinutes(1)))
                    .isInstanceOf(PlayerAlreadyLeftException.class);
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
            assertThat(player.getDeletedAt()).contains(NOW);
        }

        @Test
        @DisplayName("끝난 신청으로는 러닝을 시작하지 못한다")
        void cannotStartAfterLeave() {
            // given
            RunningPlayer player = request();
            player.leave(false, NOW);

            // when & then
            assertThatThrownBy(player::start)
                    .isInstanceOf(PlayerAlreadyLeftException.class);
        }
    }

    @Nested
    @DisplayName("러닝 진행 테스트")
    class ProgressTest {

        @Test
        @DisplayName("신청에서 러닝, 완주까지 간다")
        void joinedToRunningToCompleted() {
            // given
            RunningPlayer player = request();

            // when
            player.start();
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING);
            assertThat(player.isActive()).isTrue();
            player.complete(NOW);

            // then
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.COMPLETED);
            assertThat(player.getStatus().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("완주해도 신청은 끝나 다음 매칭을 걸 수 있다")
        void completeClosesRequest() {
            // given
            RunningPlayer player = running();

            // when
            player.complete(NOW);

            // then -> deleted_at이 NULL로 남으면 중복 신청 검사에 걸려 다음 매칭을 못 건다
            assertThat(player.getDeletedAt()).contains(NOW);
            assertThat(player.isActive()).isFalse();
        }

        @Test
        @DisplayName("시작하지 않은 참가자는 완주할 수 없다")
        void cannotCompleteBeforeStart() {
            // given
            RunningPlayer player = request();

            // when & then
            assertThatThrownBy(() -> player.complete(NOW))
                    .isInstanceOf(InvalidPlayerStatusTransitionException.class);
            assertThat(player.getDeletedAt()).isEmpty();
        }

        @Test
        @DisplayName("완주한 참가자는 더 이상 상태가 바뀌지 않는다")
        void completedIsClosed() {
            // given
            RunningPlayer player = running();
            player.complete(NOW);

            // when & then
            assertThatThrownBy(() -> player.leave(true, NOW.plusMinutes(1)))
                    .isInstanceOf(PlayerAlreadyLeftException.class);
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.COMPLETED);
        }

        @Test
        @DisplayName("이미 러닝 중인 참가자는 다시 시작하지 못한다")
        void cannotStartTwice() {
            // given
            RunningPlayer player = running();

            // when & then
            assertThatThrownBy(player::start)
                    .isInstanceOf(InvalidPlayerStatusTransitionException.class);
        }
    }
}
