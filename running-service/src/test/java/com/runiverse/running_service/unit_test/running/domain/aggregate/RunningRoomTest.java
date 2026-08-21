package com.runiverse.running_service.unit_test.running.domain.aggregate;

import com.runiverse.running_service.domain.running.aggregate.RoomSession;
import com.runiverse.running_service.domain.running.aggregate.RunningRoom;
import com.runiverse.running_service.domain.running.exception.AlreadyRoomPlayerException;
import com.runiverse.running_service.domain.running.exception.InvalidCloseAtException;
import com.runiverse.running_service.domain.running.exception.InvalidRoomStatusTransitionException;
import com.runiverse.running_service.domain.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.domain.running.exception.PlayerAlreadyLeftException;
import com.runiverse.running_service.domain.running.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.vo.Pace;
import com.runiverse.running_service.domain.running.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.vo.RunningRoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningRoomTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 20, 6, 0);
    private static final LocalDateTime CLOSE = START.minusMinutes(10);
    private static final int HOST_PACE = 330;      // 5분 30초/km
    private static final int TARGET_DISTANCE = 5_000;
    private static final long HOST = 1L;

    // 5km / 5분30초 페이스로 모집 중인 매칭 방 — 각 테스트는 여기서 한 군데만 어긋뜨린다
    private static RunningRoom matchRoom() {
        return RunningRoom.openMatch(HOST, HOST_PACE, TARGET_DISTANCE, START, CLOSE);
    }

    private static RunningRoom soloRoom() {
        return RunningRoom.openSolo(HOST, HOST_PACE, TARGET_DISTANCE, START);
    }

    private static RoomSession sessionOf(RunningRoom room, long runningPlayerId) {
        return room.getSessions().stream()
                .filter(s -> s.isSamePlayer(new RunningPlayerId(runningPlayerId)))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("방 생성 테스트")
    class OpenTest {

        @Test
        @DisplayName("솔로 방은 모집 없이 STARTED로 태어난다")
        void openSoloStartsImmediately() {
            // when
            RunningRoom room = soloRoom();

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            assertThat(room.getCloseAt()).isEmpty();   // 솔로는 모집 단계가 없다
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getPlayerCount().max()).isEqualTo(1);
            assertThat(room.getSessions()).hasSize(1);
            assertThat(sessionOf(room, HOST).isConnected()).isTrue();
        }

        @Test
        @DisplayName("매칭 방은 1인 4자리 MATCHING 상태로 태어난다")
        void openMatchStartsRecruiting() {
            // when
            RunningRoom room = matchRoom();

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
            assertThat(room.getCloseAt()).contains(CLOSE);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getPlayerCount().max()).isEqualTo(4);
            assertThat(room.getSessions()).hasSize(1);
        }

        @Test
        @DisplayName("저장 전 방은 식별자가 없다")
        void newRoomHasNoId() {
            // when
            RunningRoom room = matchRoom();

            // then
            assertThat(room.isNew()).isTrue();
            assertThat(room.getRunningRoomId()).isEmpty();
        }

        @Test
        @DisplayName("매칭 방의 모집 마감은 시작 시각보다 앞서야 한다")
        void closeAtMustBeBeforeStartAt() {
            // when & then -> 마감이 시작과 같거나 뒤면 모집 단계가 성립하지 않는다
            assertThatThrownBy(() ->
                    RunningRoom.openMatch(HOST, HOST_PACE, TARGET_DISTANCE, START, START))
                    .isInstanceOf(InvalidCloseAtException.class);
        }

        @Test
        @DisplayName("매칭 방은 모집 마감 없이 만들 수 없다")
        void matchRoomRequiresCloseAt() {
            // when & then
            assertThatThrownBy(() ->
                    RunningRoom.openMatch(HOST, HOST_PACE, TARGET_DISTANCE, START, null))
                    .isInstanceOf(InvalidCloseAtException.class);
        }
    }

    @Nested
    @DisplayName("합류 테스트")
    class JoinTest {

        @Test
        @DisplayName("페이스가 가까우면 모집 중인 방에 합류한다")
        void joinMatchingRoom() {
            // given
            RunningRoom room = matchRoom();

            // when
            room.join(2L, new Pace(340));

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getSessions()).hasSize(2);
            assertThat(sessionOf(room, 2L).isConnected()).isTrue();
            assertThat(sessionOf(room, 2L).getLeaveCount().value()).isZero();
        }

        @Test
        @DisplayName("페이스 차가 30초/km까지는 합류할 수 있다")
        void joinAtPaceTolerance() {
            // given
            RunningRoom room = matchRoom();

            // when -> 경계값은 허용한다
            room.join(2L, new Pace(HOST_PACE + 30));
            room.join(3L, new Pace(HOST_PACE - 30));

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(3);
        }

        @Test
        @DisplayName("페이스 차가 30초/km를 넘으면 합류하지 못한다")
        void rejectTooDistantPace() {
            // given
            RunningRoom room = matchRoom();

            // when & then
            assertThatThrownBy(() -> room.join(2L, new Pace(HOST_PACE + 31)))
                    .isInstanceOf(RoomNotJoinableException.class);
        }

        @Test
        @DisplayName("정원이 찬 방에는 합류하지 못한다")
        void rejectJoinWhenFull() {
            // given
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));
            room.join(3L, new Pace(HOST_PACE));
            room.join(4L, new Pace(HOST_PACE));

            // when & then -> 자리는 4개뿐이다
            assertThat(room.getPlayerCount().isFull()).isTrue();
            assertThatThrownBy(() -> room.join(5L, new Pace(HOST_PACE)))
                    .isInstanceOf(RoomNotJoinableException.class);
        }

        @Test
        @DisplayName("이미 이 방의 참가자면 다시 합류하지 못한다")
        void rejectDuplicatePlayer() {
            // given
            RunningRoom room = matchRoom();

            // when & then -> 한 플레이어는 최대 한 방
            assertThatThrownBy(() -> room.join(HOST, new Pace(HOST_PACE)))
                    .isInstanceOf(AlreadyRoomPlayerException.class);
        }

        @Test
        @DisplayName("모집이 끝난 방에는 합류하지 못한다")
        void rejectJoinAfterMatched() {
            // given
            RunningRoom room = matchRoom();
            room.closeMatching();

            // when & then
            assertThatThrownBy(() -> room.join(2L, new Pace(HOST_PACE)))
                    .isInstanceOf(RoomNotJoinableException.class);
        }

        @Test
        @DisplayName("솔로 방에는 합류하지 못한다")
        void rejectJoinSoloRoom() {
            // given
            RunningRoom room = soloRoom();

            // when & then -> 솔로 방은 STARTED라 후보가 되지 않는다
            assertThatThrownBy(() -> room.join(2L, new Pace(HOST_PACE)))
                    .isInstanceOf(RoomNotJoinableException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이 테스트")
    class StatusTest {

        @Test
        @DisplayName("모집 마감 후 시작하고 종료한다")
        void matchedThenStartedThenFinished() {
            // given
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));

            // when
            room.closeMatching();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
            room.start();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            room.finish();

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        }

        @Test
        @DisplayName("1인만 남은 방도 마감 확정돼 혼자 뛴다")
        void singlePlayerRoomIsStillMatched() {
            // given
            RunningRoom room = matchRoom();

            // when -> 인원이 안 차도 취소하지 않는다
            room.closeMatching();
            room.start();

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
        }

        @Test
        @DisplayName("모집 중인 방은 마감 없이 시작하지 못한다")
        void cannotStartWhileMatching() {
            // given
            RunningRoom room = matchRoom();

            // when & then
            assertThatThrownBy(room::start)
                    .isInstanceOf(InvalidRoomStatusTransitionException.class);
        }

        @Test
        @DisplayName("시작한 방은 취소할 수 없다")
        void startedRoomCannotBeCancelled() {
            // given
            RunningRoom room = soloRoom();   // 솔로는 STARTED로 태어난다

            // when & then -> 취소하면 FINISHED에 닿지 못해 기록이 사라진다
            assertThatThrownBy(room::cancel)
                    .isInstanceOf(InvalidRoomStatusTransitionException.class);
        }

        @Test
        @DisplayName("종료된 방은 더 이상 상태가 바뀌지 않는다")
        void finishedRoomIsTerminal() {
            // given
            RunningRoom room = soloRoom();
            room.finish();

            // when & then
            assertThatThrownBy(room::cancel)
                    .isInstanceOf(InvalidRoomStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("이탈·연결 테스트")
    class SessionTest {

        @Test
        @DisplayName("이탈하면 인원이 줄고 관계는 남는다")
        void leaveKeepsSession() {
            // given
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));

            // when
            room.leave(2L);

            // then -> 어느 방에서 나갔는지가 페널티 근거라 관계는 지우지 않는다
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getSessions()).hasSize(2);
            assertThat(sessionOf(room, 2L).isConnected()).isFalse();
            assertThat(sessionOf(room, 2L).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("러닝이 시작된 뒤에는 마지막 참가자가 빠져도 방을 닫지 않는다")
        void startedRoomIsNotCancelledWhenEmptied() {
            // given -> 1인으로 확정돼 혼자 뛰는 방
            RunningRoom room = matchRoom();
            room.closeMatching();
            room.start();

            // when -> 혼자 뛰던 사람이 조기 종료한다
            room.leave(HOST);

            // then -> 닫으면 FINISHED에 닿지 못해 기록이 사라진다
            assertThat(room.getPlayerCount().current()).isZero();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            room.finish();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        }

        @Test
        @DisplayName("마지막 참가자가 이탈하면 방이 취소된다")
        void lastLeaveCancelsRoom() {
            // given
            RunningRoom room = matchRoom();

            // when
            room.leave(HOST);

            // then -> 남은 사람이 없으면 방도 없다
            assertThat(room.getPlayerCount().current()).isZero();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        }

        @Test
        @DisplayName("1명이 남으면 방은 유지된다")
        void roomSurvivesWithSinglePlayer() {
            // given
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));
            room.closeMatching();

            // when -> 1인이 돼도 혼자 뛴다
            room.leave(2L);

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
        }

        @Test
        @DisplayName("다시 들어오면 세션을 새로 만들지 않고 되살린다")
        void rejoinRestoresSession() {
            // given
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));
            room.leave(2L);

            // when
            room.rejoin(2L);

            // then -> 관계는 하나뿐이고 나간 이력은 남는다
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getSessions()).hasSize(2);
            assertThat(sessionOf(room, 2L).isConnected()).isTrue();
            assertThat(sessionOf(room, 2L).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("러닝이 시작된 뒤에도 다시 들어올 수 있다")
        void rejoinAfterStarted() {
            // given
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));
            room.closeMatching();
            room.start();
            room.leave(2L);

            // when -> 재입장은 모집 조건을 타지 않는다
            room.rejoin(2L);

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
        }

        @Test
        @DisplayName("나갔다 들어오기를 반복하면 이탈 횟수가 쌓인다")
        void leaveCountAccumulates() {
            // given
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));

            // when
            room.leave(2L);
            room.rejoin(2L);
            room.leave(2L);

            // then -> 페널티 판정 근거가 된다
            assertThat(sessionOf(room, 2L).getLeaveCount().value()).isEqualTo(2);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 나간 사람이 또 이탈해도 남은 참가자의 방은 취소되지 않는다")
        void leaveTwiceWithoutRejoinDoesNotCancelRoom() {
            // given -> 2인 방에서 2L이 이미 나갔다
            RunningRoom room = matchRoom();
            room.join(2L, new Pace(HOST_PACE));
            room.leave(2L);

            // when & then -> WS 재연결·이벤트 중복으로 leave가 한 번 더 들어와도 막혀야 한다
            assertThatThrownBy(() -> room.leave(2L))
                    .isInstanceOf(PlayerAlreadyLeftException.class);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);   // HOST는 아직 방에 있다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
            assertThat(sessionOf(room, 2L).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("종료된 방에서 마지막 참가자가 나가도 방은 취소되지 않는다")
        void leaveOnFinishedRoomDoesNotCancelRoom() {
            // given -> 러닝이 끝난 뒤 마지막 참가자가 연결을 끊는다
            RunningRoom room = matchRoom();
            room.closeMatching();
            room.start();
            room.finish();

            // when
            room.leave(HOST);

            // then -> 끝난 방은 닫지 않는다. 취소로 덮으면 기록이 사라진다
            assertThat(room.getPlayerCount().current()).isZero();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
            assertThat(sessionOf(room, HOST).isConnected()).isFalse();
            assertThat(sessionOf(room, HOST).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 방에 있는 사람은 다시 들어오지 못한다")
        void rejectRejoinWhenAlreadyIn() {
            // given
            RunningRoom room = matchRoom();

            // when & then
            assertThatThrownBy(() -> room.rejoin(HOST))
                    .isInstanceOf(AlreadyRoomPlayerException.class);
        }

        @Test
        @DisplayName("취소된 방에는 다시 들어오지 못한다")
        void rejectRejoinToCancelledRoom() {
            // given
            RunningRoom room = matchRoom();
            room.leave(HOST);   // 마지막 1인이 나가 방이 취소된다

            // when & then
            assertThatThrownBy(() -> room.rejoin(HOST))
                    .isInstanceOf(RoomNotJoinableException.class);
        }

        @Test
        @DisplayName("이 방의 참가자가 아니면 다시 들어오지 못한다")
        void rejectRejoinUnknownPlayer() {
            // given
            RunningRoom room = matchRoom();

            // when & then -> 처음 들어오는 건 join()이 받는다
            assertThatThrownBy(() -> room.rejoin(99L))
                    .isInstanceOf(NotRoomPlayerException.class);
        }

        @Test
        @DisplayName("이 방의 참가자가 아니면 이탈시켜도 인원이 줄지 않는다")
        void unknownPlayerLeaveDoesNotChangeRoom() {
            // given
            RunningRoom room = matchRoom();

            // when & then -> 예외가 나면 방은 아무것도 바뀌지 않아야 한다
            assertThatThrownBy(() -> room.leave(99L))
                    .isInstanceOf(NotRoomPlayerException.class);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        }
    }

    @Nested
    @DisplayName("평균 페이스 테스트")
    class AvgPaceTest {

        @Test
        @DisplayName("참가자 페이스로 방 평균을 다시 계산한다")
        void recalculateAvgPace() {
            // given
            RunningRoom room = matchRoom();

            // when
            room.recalculateAvgPace(List.of(new Pace(300), new Pace(360)));

            // then
            assertThat(room.getAvgPace().secondsPerKm()).isEqualTo(330);
        }

        @Test
        @DisplayName("참가자가 없으면 기존 평균을 유지한다")
        void keepAvgPaceWhenNoPlayers() {
            // given
            RunningRoom room = matchRoom();

            // when
            room.recalculateAvgPace(List.of());

            // then
            assertThat(room.getAvgPace().secondsPerKm()).isEqualTo(HOST_PACE);
        }
    }
}
