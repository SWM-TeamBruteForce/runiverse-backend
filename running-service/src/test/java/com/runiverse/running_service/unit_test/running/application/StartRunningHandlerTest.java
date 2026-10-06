package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningHandler;
import com.runiverse.running_service.application.running.command.start.StartRunningResult;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.out.LockRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 시작 단위 테스트")
public class StartRunningHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long PLAYER_ID = 42L;
    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 330;                              // 5분 30초/km
    private static final LocalDateTime PAST = LocalDateTime.now().minusMinutes(5);
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusMinutes(5);

    // 취소와 같은 행을 고치므로 잠그고 읽는 포트를 쓴다 — 잠금 없는 조회 포트는 남겨두지 않았다
    @Mock
    private LockRunningRoomPort lockRunningRoomPort;

    @Mock
    private UpdateRunningRoomPort updateRunningRoomPort;

    @Mock
    private LockRunningPlayerPort lockRunningPlayerPort;

    @Mock
    private UpdateRunningPlayerPort updateRunningPlayerPort;

    @InjectMocks
    private StartRunningHandler handler;

    // 시작 시각이 지난 4자리 매칭 방 — 각 테스트는 여기서 한 군데만 어긋뜨린다
    private static RunningRoom room(RunningRoomStatus status) {
        return room(status, PAST, true, 1, 4);
    }

    private static RunningRoom room(RunningRoomStatus status, LocalDateTime startAt,
                                    boolean connected, int currentPlayerCount, int maxPlayerCount) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(status)
                .startAt(startAt)
                // 종료 상태는 닫힌 시각이 있어야 복원된다
                .closeAt(status.isTerminal() ? startAt.plusMinutes(1) : null)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(maxPlayerCount)
                .sessions(List.of(new SessionDraft(
                        new UserId(USER_ID), new RunningPlayerId(PLAYER_ID), 0, connected)))
                .build();
    }

    private static RunningPlayer player(RunningPlayerStatus status) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(USER_ID)
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(5_000)
                .startAt(PAST)
                .build();
    }

    private static RoomSession sessionOf(RunningRoom room) {
        return room.getSessions().stream()
                .filter(session -> session.isSameUser(new UserId(USER_ID)))
                .findFirst()
                .orElseThrow();
    }

    private void givenStore(RunningRoom room, RunningPlayer player) {
        given(lockRunningPlayerPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.of(player));
        given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private StartRunningResult start() {
        return handler.handle(new StartRunningCommand(USER_ID, ROOM_ID));
    }

    @Nested
    @DisplayName("시작 처리 테스트")
    class StartTest {

        @Test
        @DisplayName("확정된 방에 들어오면 방과 참가자가 함께 시작한다")
        void startsRoomAndPlayer() {
            // given
            RunningRoom room = room(RunningRoomStatus.MATCHED);
            RunningPlayer player = player(RunningPlayerStatus.JOINED);
            givenStore(room, player);

            // when
            StartRunningResult result = start();

            // then
            assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING);
        }

        @Test
        @DisplayName("도메인은 영속성 밖의 객체라 갱신을 명시적으로 되돌려 쓴다")
        void writesBackBothAggregates() {
            // given
            RunningRoom room = room(RunningRoomStatus.MATCHED);
            RunningPlayer player = player(RunningPlayerStatus.JOINED);
            givenStore(room, player);

            // when
            start();

            // then -> 이 호출이 빠지면 상태 전이가 통째로 사라진다
            verify(updateRunningRoomPort).update(room);
            verify(updateRunningPlayerPort).update(player);
        }

        @Test
        @DisplayName("이미 시작한 방에 다시 보내도 아무것도 바뀌지 않는다")
        void isIdempotentOnReconnect() {
            // given -> 러닝 중 연결이 끊겼다 다시 붙은 상황
            RunningRoom room = room(RunningRoomStatus.STARTED);
            RunningPlayer player = player(RunningPlayerStatus.RUNNING);
            givenStore(room, player);

            // when -> 클라는 최초 진입인지 재연결인지 구분하지 않고 같은 메시지를 보낸다
            StartRunningResult result = start();

            // then
            assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING);
        }

        @Test
        @DisplayName("먼저 시작한 방에 늦게 들어온 참가자는 자기만 시작한다")
        void startsOnlyPlayerWhenRoomAlreadyStarted() {
            // given -> 다른 참가자가 이미 방을 STARTED로 올려둔 상태
            RunningRoom room = room(RunningRoomStatus.STARTED);
            RunningPlayer player = player(RunningPlayerStatus.JOINED);
            givenStore(room, player);

            // when
            start();

            // then -> 시작 시각에 앱을 안 켠 사람까지 일괄로 RUNNING이 되지는 않는다
            assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
            assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.RUNNING);
        }
    }

    @Nested
    @DisplayName("재입장 테스트")
    class RejoinTest {

        @Test
        @DisplayName("나갔던 참가자가 돌아오면 세션이 되살아나고 인원이 는다")
        void rejoinRestoresSession() {
            // given -> 2인 방에서 나가 1인이 된 상태
            RunningRoom room = room(RunningRoomStatus.STARTED, PAST, false, 1, 4);
            RunningPlayer player = player(RunningPlayerStatus.RUNNING);
            givenStore(room, player);

            // when
            start();

            // then
            assertThat(sessionOf(room).isConnected()).isTrue();
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
        }

        @Test
        @DisplayName("연결만 끊겼던 참가자는 인원이 늘지 않는다")
        void reconnectDoesNotChangePlayerCount() {
            // given -> 방을 나간 적은 없다(is_connected=true)
            RunningRoom room = room(RunningRoomStatus.STARTED, PAST, true, 1, 4);
            RunningPlayer player = player(RunningPlayerStatus.RUNNING);
            givenStore(room, player);

            // when
            start();

            // then -> 연결 끊김은 방 나가기가 아니다
            assertThat(room.getPlayerCount().current()).isEqualTo(1);
        }

        @Test
        @DisplayName("그새 자리가 찼으면 돌아오지 못한다")
        void rejectRejoinWhenFull() {
            // given -> 내가 나간 사이 다른 사람이 들어와 4자리가 다 찼다
            RunningRoom room = room(RunningRoomStatus.STARTED, PAST, false, 4, 4);
            RunningPlayer player = player(RunningPlayerStatus.RUNNING);
            givenStore(room, player);

            // when & then -> 도메인의 RoomIsFullException이 새지 않게 미리 거른다
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }
    }

    @Nested
    @DisplayName("거부 테스트")
    class RejectTest {

        @Test
        @DisplayName("없는 방이면 거부한다")
        void rejectUnknownRoom() {
            // given -> 신청은 살아 있는데 방이 없다. 신청을 먼저 잠그므로 그 스텁이 함께 필요하다
            given(lockRunningPlayerPort.lockActive(new UserId(USER_ID)))
                    .willReturn(Optional.of(player(RunningPlayerStatus.JOINED)));
            given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID)))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(RunningRoomNotFoundException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("활성 신청이 없으면 이 방 사람일 수 없다")
        void rejectWithoutActivePlayer() {
            // given -> 취소가 먼저 커밋돼 deleted_at이 찍힌 상태다.
            // 잠그고 읽으니 그 커밋이 보이고, 조회가 비어 여기서 걸린다 — 덮어쓰기로 되살아나지 않는다
            given(lockRunningPlayerPort.lockActive(new UserId(USER_ID)))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(NotRoomPlayerException.class);
            // 방까지 가지 않는다 — 신청이 없으면 방을 잠글 이유도 없다
            verifyNoInteractions(lockRunningRoomPort, updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("다른 방에 배정된 신청이면 거부한다")
        void rejectPlayerOfAnotherRoom() {
            // given -> 이 방의 세션은 남의 것뿐이다
            RunningRoom room = RunningRoom.builder()
                    .runningRoomId(ROOM_ID)
                    .type(RunningRoomType.MATCH)
                    .status(RunningRoomStatus.MATCHED)
                    .startAt(PAST)
                    .avgPace(AVG_PACE)
                    .currentPlayerCount(1)
                    .maxPlayerCount(4)
                    // 세션의 키가 유저라 "남의 방"은 다른 유저의 세션으로 만든다
                    .sessions(List.of(new SessionDraft(
                            new UserId(UuidCreator.getTimeOrderedEpoch()),
                            new RunningPlayerId(99L), 0, true)))
                    .build();
            givenStore(room, player(RunningPlayerStatus.JOINED));

            // when & then
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(NotRoomPlayerException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("예전에 거쳐 간 방이면 지금 신청의 방이 아니므로 거부한다")
        void rejectRoomOfPastApplication() {
            // given -> 이 방의 내 세션은 끝난 옛 신청을 가리킨다. 지금 활성 신청은 다른 방에 있다
            RunningRoom room = RunningRoom.builder()
                    .runningRoomId(ROOM_ID)
                    .type(RunningRoomType.MATCH)
                    .status(RunningRoomStatus.STARTED)
                    .startAt(PAST)
                    .avgPace(AVG_PACE)
                    .currentPlayerCount(1)
                    .maxPlayerCount(4)
                    .sessions(List.of(new SessionDraft(
                            new UserId(USER_ID), new RunningPlayerId(PLAYER_ID - 1), 0, false)))
                    .build();
            givenStore(room, player(RunningPlayerStatus.JOINED));

            // when & then -> 받아 주면 다른 방의 신청이 이 방에 되살아나 RUNNING이 된다
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(NotRoomPlayerException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("시작 시각 전이면 거부한다")
        void rejectBeforeStartAt() {
            // given -> 매칭 클라가 카운트다운도 전에 쐈다
            givenStore(room(RunningRoomStatus.MATCHED, FUTURE, true, 1, 4),
                    player(RunningPlayerStatus.JOINED));

            // when & then
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("아직 모집 중인 방은 여기서 확정해주지 않는다")
        void rejectMatchingRoom() {
            // given -> 마감 스케줄러가 아직 MATCHED로 올리지 않았다
            givenStore(room(RunningRoomStatus.MATCHING), player(RunningPlayerStatus.JOINED));

            // when & then -> 스케줄러가 할 일을 핸들러가 덮으면 장애가 조용히 묻힌다
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("끝난 방에는 들어가지 못한다")
        void rejectFinishedRoom() {
            // given
            givenStore(room(RunningRoomStatus.FINISHED), player(RunningPlayerStatus.RUNNING));

            // when & then
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("취소된 방에는 들어가지 못한다")
        void rejectCancelledRoom() {
            // given
            givenStore(room(RunningRoomStatus.CANCELLED), player(RunningPlayerStatus.JOINED));

            // when & then
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }

        @Test
        @DisplayName("아직 수락하지 않은 초대는 시작하지 못한다")
        void rejectInvitedPlayer() {
            // given -> loadActive는 deleted_at만 보므로 INVITED도 살아 있는 채로 올라온다
            givenStore(room(RunningRoomStatus.MATCHED), player(RunningPlayerStatus.INVITED));

            // when & then
            assertThatThrownBy(StartRunningHandlerTest.this::start)
                    .isInstanceOf(RunningNotStartableException.class);
            verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort);
        }
    }
}
