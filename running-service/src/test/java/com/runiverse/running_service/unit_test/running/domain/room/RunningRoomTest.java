package com.runiverse.running_service.unit_test.running.domain.room;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.exception.AlreadyLeftRoomException;
import com.runiverse.running_service.domain.running.room.exception.AlreadyRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.InvalidCloseAtException;
import com.runiverse.running_service.domain.running.room.exception.InvalidRoomStatusTransitionException;
import com.runiverse.running_service.domain.running.room.exception.NotRoomPlayerException;
import com.runiverse.running_service.domain.running.room.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunningRoomTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 20, 6, 0);
    private static final LocalDateTime CLOSED = START.plusMinutes(40);   // 방이 닫힌 시각
    private static final LocalDateTime LEFT_AT = START.plusMinutes(5);   // 이탈 시각
    private static final int HOST_PACE = 330;      // 5분 30초/km
    private static final int TARGET_DISTANCE = 5_000;
    private static final Map<Long, UserId> USERS = new ConcurrentHashMap<>();
    private static final RunningPlayerId HOST = player(1L);
    private static final UserId HOST_USER = user(1L);

    // 방 API가 원시 Long이 아니라 식별자 VO를 받는다 — 테스트도 같은 타입으로 부른다
    private static RunningPlayerId player(long value) {
        return new RunningPlayerId(value);
    }

    // 세션의 키는 유저다 — 번호마다 고정된 UUID를 주어 player(n)과 같은 참가자로 읽히게 한다.
    // UserId가 v7만 받아 직접 조립하지 못하므로 생성기 결과를 번호에 묶어 캐시한다
    private static UserId user(long value) {
        return USERS.computeIfAbsent(value, key -> new UserId(UuidCreator.getTimeOrderedEpoch()));
    }

    // 5km / 5분30초 페이스로 모집 중인 매칭 방 — 각 테스트는 여기서 한 군데만 어긋뜨린다
    private static RunningRoom matchRoom() {
        return RunningRoom.openMatch(HOST_USER, HOST, HOST_PACE, TARGET_DISTANCE, START);
    }

    private static RunningRoom soloRoom() {
        return RunningRoom.openSolo(HOST_USER, HOST, HOST_PACE, TARGET_DISTANCE, START);
    }

    // DB 복원 경로 — 어댑터가 builder로 조립하는 모양 그대로다
    private static RunningRoom restoredRoom(RunningRoomStatus status, LocalDateTime closeAt) {
        return RunningRoom.builder()
                .runningRoomId(1L)
                .type(RunningRoomType.MATCH)
                .status(status)
                .startAt(START)
                .closeAt(closeAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(HOST_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .build();
    }

    private static RoomSession sessionOf(RunningRoom room, UserId userId) {
        return room.getSessions().stream()
                .filter(session -> session.isSameUser(userId))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("방 생성 테스트")
    class OpenTest {

        @Test
        @DisplayName("솔로 방은 모집 없이 MATCHED로 태어난다")
        void openSoloStartsMatched() {
            // when
            RunningRoom room = soloRoom();

            // then -> 태어나는 지점만 다르고 이후 흐름은 매칭과 같다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getPlayerCount().max()).isEqualTo(1);
            assertThat(room.getSessions()).hasSize(1);
            assertThat(sessionOf(room, HOST_USER).isConnected()).isTrue();
        }

        @Test
        @DisplayName("매칭 방은 1인 4자리 MATCHING 상태로 태어난다")
        void openMatchStartsRecruiting() {
            // when
            RunningRoom room = matchRoom();

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getPlayerCount().max()).isEqualTo(4);
            assertThat(room.getSessions()).hasSize(1);
        }

        @Test
        @DisplayName("갓 태어난 방은 종류와 무관하게 닫힌 시각이 없다")
        void newRoomIsOpen() {
            // when & then -> close_at은 모집 마감이 아니라 방이 닫힌 시각이다
            assertThat(soloRoom().getCloseAt()).isEmpty();
            assertThat(matchRoom().getCloseAt()).isEmpty();
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
        @DisplayName("종료된 방을 닫힌 시각 없이 복원하지 못한다")
        void terminalRoomRequiresCloseAt() {
            // when & then -> 상태와 닫힌 시각이 갈라진 행은 애그리거트가 거부한다
            assertThatThrownBy(() -> restoredRoom(RunningRoomStatus.FINISHED, null))
                    .isInstanceOf(InvalidCloseAtException.class);
        }

        @Test
        @DisplayName("아직 열려 있는 방은 닫힌 시각을 가질 수 없다")
        void openRoomMustNotHaveCloseAt() {
            // when & then
            assertThatThrownBy(() -> restoredRoom(RunningRoomStatus.MATCHING, CLOSED))
                    .isInstanceOf(InvalidCloseAtException.class);
        }
    }

    @Nested
    @DisplayName("합류 테스트")
    class JoinTest {

        @Test
        @DisplayName("모집 중이고 자리가 있으면 합류한다")
        void joinMatchingRoom() {
            // given
            RunningRoom room = matchRoom();

            // when
            room.join(user(2L), player(2L));

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getSessions()).hasSize(2);
            assertThat(sessionOf(room, user(2L)).isConnected()).isTrue();
            assertThat(sessionOf(room, user(2L)).getLeaveCount().value()).isZero();
        }

        @Test
        @DisplayName("방 평균과 페이스가 아무리 벌어져도 합류한다")
        void joinRegardlessOfPaceGap() {
            // given -> 방 평균은 330초/km다
            RunningRoom room = matchRoom();

            // when -> 합류는 페이스를 아예 받지 않는다. 옛 ±30초 자격은 없어졌고
            //         페이스는 후보 순서만 정한다(MatchRoomAssigner)
            room.join(user(2L), player(2L));
            room.recalculateAvgPace(List.of(new Pace(HOST_PACE), new Pace(900)));

            // then -> 570초/km 차이도 막지 않는다
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getAvgPace()).contains(new Pace(615));
        }

        @Test
        @DisplayName("정원이 찬 방에는 합류하지 못한다")
        void rejectJoinWhenFull() {
            // given
            RunningRoom room = matchRoom();
            room.join(user(2L), player(2L));
            room.join(user(3L), player(3L));
            room.join(user(4L), player(4L));

            // when & then -> 자리는 4개뿐이다
            assertThat(room.getPlayerCount().isFull()).isTrue();
            assertThatThrownBy(() -> room.join(user(5L), player(5L)))
                    .isInstanceOf(RoomNotJoinableException.class);
        }

        @Test
        @DisplayName("이미 이 방의 참가자면 다시 합류하지 못한다")
        void rejectDuplicatePlayer() {
            // given
            RunningRoom room = matchRoom();

            // when & then -> 한 플레이어는 최대 한 방
            assertThatThrownBy(() -> room.join(HOST_USER, HOST))
                    .isInstanceOf(AlreadyRoomPlayerException.class);
        }

        @Test
        @DisplayName("모집이 끝난 방에는 합류하지 못한다")
        void rejectJoinAfterMatched() {
            // given
            RunningRoom room = matchRoom();
            room.closeMatching();

            // when & then
            assertThatThrownBy(() -> room.join(user(2L), player(2L)))
                    .isInstanceOf(RoomNotJoinableException.class);
        }

        @Test
        @DisplayName("솔로 방에는 합류하지 못한다")
        void rejectJoinSoloRoom() {
            // given
            RunningRoom room = soloRoom();

            // when & then -> 솔로 방은 MATCHED로 태어나 모집 후보가 되지 않는다
            assertThatThrownBy(() -> room.join(user(2L), player(2L)))
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
            room.join(user(2L), player(2L));

            // when
            room.closeMatching();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
            room.start();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            room.finish(CLOSED);

            // then -> 종료 상태와 닫힌 시각은 한 번에 확정된다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
            assertThat(room.getCloseAt()).contains(CLOSED);
        }

        @Test
        @DisplayName("솔로 방은 확정 상태에서 바로 시작한다")
        void soloRoomStartsFromMatched() {
            // given -> 모집을 거치지 않았을 뿐 매칭 방과 같은 자리에서 출발한다
            RunningRoom room = soloRoom();

            // when
            room.start();

            // then
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            assertThat(room.getCloseAt()).isEmpty();
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
        @DisplayName("시작한 방도 취소할 수 있다")
        void startedRoomCanBeCancelled() {
            // given
            RunningRoom room = soloRoom();
            room.start();

            // when
            room.cancel(CLOSED);

            // then -> 취소는 종착 상태라 이후로는 종료로도 갈 수 없다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
            assertThat(room.getCloseAt()).contains(CLOSED);
            assertThatThrownBy(() -> room.finish(CLOSED.plusMinutes(1)))
                    .isInstanceOf(InvalidRoomStatusTransitionException.class);
        }

        @Test
        @DisplayName("종료된 방은 더 이상 상태가 바뀌지 않는다")
        void finishedRoomIsTerminal() {
            // given
            RunningRoom room = soloRoom();
            room.start();
            room.finish(CLOSED);

            // when & then
            assertThatThrownBy(() -> room.cancel(CLOSED.plusMinutes(1)))
                    .isInstanceOf(InvalidRoomStatusTransitionException.class);
        }

        @Test
        @DisplayName("전이가 거절되면 닫힌 시각도 남지 않는다")
        void rejectedTransitionKeepsCloseAtEmpty() {
            // given -> 모집 중인 방은 종료로 갈 수 없다
            RunningRoom room = matchRoom();

            // when & then -> 상태가 안 바뀌었는데 시각만 찍히면 조회가 거짓말을 한다
            assertThatThrownBy(() -> room.finish(CLOSED))
                    .isInstanceOf(InvalidRoomStatusTransitionException.class);
            assertThat(room.getCloseAt()).isEmpty();
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
            room.join(user(2L), player(2L));

            // when
            room.leave(user(2L), LEFT_AT);

            // then -> 어느 방에서 나갔는지가 페널티 근거라 관계는 지우지 않는다
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getCloseAt()).isEmpty();   // 사람이 남았으니 방은 열려 있다
            assertThat(room.getSessions()).hasSize(2);
            assertThat(sessionOf(room, user(2L)).isConnected()).isFalse();
            assertThat(sessionOf(room, user(2L)).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("러닝이 시작된 뒤에도 마지막 참가자가 빠지면 방이 닫힌다")
        void startedRoomIsCancelledWhenEmptied() {
            // given -> 1인으로 확정돼 혼자 뛰는 방
            RunningRoom room = matchRoom();
            room.closeMatching();
            room.start();

            // when -> 혼자 뛰던 사람이 나간다
            room.leave(HOST_USER, LEFT_AT);

            // then -> 남은 사람이 없으면 시작 여부와 무관하게 방도 없다
            assertThat(room.getPlayerCount().current()).isZero();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
            assertThat(room.getCloseAt()).contains(LEFT_AT);   // 마지막 이탈이 곧 방이 닫힌 시각
            assertThat(sessionOf(room, HOST_USER).isConnected()).isFalse();
            assertThat(sessionOf(room, HOST_USER).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("마지막 참가자가 이탈하면 방이 취소된다")
        void lastLeaveCancelsRoom() {
            // given
            RunningRoom room = matchRoom();

            // when
            room.leave(HOST_USER, LEFT_AT);

            // then -> 남은 사람이 없으면 방도 없다
            assertThat(room.getPlayerCount().current()).isZero();
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
            assertThat(room.getCloseAt()).contains(LEFT_AT);
        }

        @Test
        @DisplayName("1명이 남으면 방은 유지된다")
        void roomSurvivesWithSinglePlayer() {
            // given
            RunningRoom room = matchRoom();
            room.join(user(2L), player(2L));
            room.closeMatching();

            // when -> 1인이 돼도 혼자 뛴다
            room.leave(user(2L), LEFT_AT);

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
            assertThat(room.getCloseAt()).isEmpty();
        }

        @Test
        @DisplayName("다시 들어오면 세션을 새로 만들지 않고 되살린다")
        void rejoinRestoresSession() {
            // given
            RunningRoom room = matchRoom();
            room.join(user(2L), player(2L));
            room.leave(user(2L), LEFT_AT);

            // when
            room.rejoin(user(2L));

            // then -> 관계는 하나뿐이고 나간 이력은 남는다
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getSessions()).hasSize(2);
            assertThat(sessionOf(room, user(2L)).isConnected()).isTrue();
            assertThat(sessionOf(room, user(2L)).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("러닝이 시작된 뒤에도 다시 들어올 수 있다")
        void rejoinAfterStarted() {
            // given
            RunningRoom room = matchRoom();
            room.join(user(2L), player(2L));
            room.closeMatching();
            room.start();
            room.leave(user(2L), LEFT_AT);

            // when -> 재입장은 모집 조건을 타지 않는다
            room.rejoin(user(2L));

            // then
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
        }

        @Test
        @DisplayName("나갔다 들어오기를 반복하면 이탈 횟수가 쌓인다")
        void leaveCountAccumulates() {
            // given
            RunningRoom room = matchRoom();
            room.join(user(2L), player(2L));

            // when
            room.leave(user(2L), LEFT_AT);
            room.rejoin(user(2L));
            room.leave(user(2L), LEFT_AT.plusMinutes(1));

            // then -> 페널티 판정 근거가 된다
            assertThat(sessionOf(room, user(2L)).getLeaveCount().value()).isEqualTo(2);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 나간 사람이 또 이탈해도 남은 참가자의 방은 취소되지 않는다")
        void leaveTwiceWithoutRejoinDoesNotCancelRoom() {
            // given -> 2인 방에서 2L이 이미 나갔다
            RunningRoom room = matchRoom();
            room.join(user(2L), player(2L));
            room.leave(user(2L), LEFT_AT);

            // when & then -> WS 재연결·이벤트 중복으로 leave가 한 번 더 들어와도 막혀야 한다
            assertThatThrownBy(() -> room.leave(user(2L), LEFT_AT.plusMinutes(1)))
                    .isInstanceOf(AlreadyLeftRoomException.class);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);   // HOST는 아직 방에 있다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
            assertThat(room.getCloseAt()).isEmpty();
            assertThat(sessionOf(room, user(2L)).getLeaveCount().value()).isEqualTo(1);
        }

        @Test
        @DisplayName("종료된 방의 이탈이 거절되면 인원·세션도 그대로다")
        void rejectedLeaveOnFinishedRoomKeepsRoomIntact() {
            // given -> 러닝이 끝난 뒤 마지막 참가자가 연결을 끊는다
            RunningRoom room = matchRoom();
            room.closeMatching();
            room.start();
            room.finish(CLOSED);

            // when & then -> 취소로 못 가는 방이면 아무것도 바뀌지 않아야 한다
            assertThatThrownBy(() -> room.leave(HOST_USER, CLOSED.plusMinutes(1)))
                    .isInstanceOf(InvalidRoomStatusTransitionException.class);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
            assertThat(room.getCloseAt()).contains(CLOSED);   // 종료 때 찍힌 값이 덮이지 않는다
            assertThat(sessionOf(room, HOST_USER).isConnected()).isTrue();
            assertThat(sessionOf(room, HOST_USER).getLeaveCount().value()).isZero();
        }

        @Test
        @DisplayName("이미 방에 있는 사람은 다시 들어오지 못한다")
        void rejectRejoinWhenAlreadyIn() {
            // given
            RunningRoom room = matchRoom();

            // when & then
            assertThatThrownBy(() -> room.rejoin(HOST_USER))
                    .isInstanceOf(AlreadyRoomPlayerException.class);
        }

        @Test
        @DisplayName("취소된 방에는 다시 들어오지 못한다")
        void rejectRejoinToCancelledRoom() {
            // given
            RunningRoom room = matchRoom();
            room.leave(HOST_USER, LEFT_AT);   // 마지막 1인이 나가 방이 취소된다

            // when & then
            assertThatThrownBy(() -> room.rejoin(HOST_USER))
                    .isInstanceOf(RoomNotJoinableException.class);
        }

        @Test
        @DisplayName("이 방의 참가자가 아니면 다시 들어오지 못한다")
        void rejectRejoinUnknownPlayer() {
            // given
            RunningRoom room = matchRoom();

            // when & then -> 처음 들어오는 건 join()이 받는다
            assertThatThrownBy(() -> room.rejoin(user(99L)))
                    .isInstanceOf(NotRoomPlayerException.class);
        }

        @Test
        @DisplayName("이 방의 참가자가 아니면 이탈시켜도 인원이 줄지 않는다")
        void unknownPlayerLeaveDoesNotChangeRoom() {
            // given
            RunningRoom room = matchRoom();

            // when & then -> 예외가 나면 방은 아무것도 바뀌지 않아야 한다
            assertThatThrownBy(() -> room.leave(user(99L), LEFT_AT))
                    .isInstanceOf(NotRoomPlayerException.class);
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
            assertThat(room.getCloseAt()).isEmpty();
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
            assertThat(room.getAvgPace()).map(Pace::secondsPerKm).contains(330);
        }

        @Test
        @DisplayName("참가자가 없으면 평균도 사라진다")
        void clearAvgPaceWhenNoPlayers() {
            // given
            RunningRoom room = matchRoom();

            // when -> 마지막 값을 남기면 나간 사람 기준이 유령으로 떠돈다
            room.recalculateAvgPace(List.of());

            // then
            assertThat(room.getAvgPace()).isEmpty();
        }

        @Test
        @DisplayName("갓 태어난 방은 개설자의 페이스를 갖는다")
        void newRoomHasOpenerPace() {
            // when & then -> 1인 방이라 개설자 페이스가 곧 평균이다
            assertThat(matchRoom().getAvgPace()).map(Pace::secondsPerKm).contains(HOST_PACE);
            assertThat(soloRoom().getAvgPace()).map(Pace::secondsPerKm).contains(HOST_PACE);
        }

        @Test
        @DisplayName("평균이 없는 방에는 합류하지 못한다")
        void cannotJoinRoomWithoutAvgPace() {
            // given -> 전원이 빠져 평균이 사라진 방
            RunningRoom room = matchRoom();
            room.recalculateAvgPace(List.of());

            // when & then -> 페이스 근접을 판정할 기준이 없다
            assertThat(room.canJoin()).isFalse();
        }
    }
}
