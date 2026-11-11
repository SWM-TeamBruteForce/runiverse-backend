package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.RoomChatAccessPolicy;
import com.runiverse.running_service.application.match.exception.RoomChatClosedException;
import com.runiverse.running_service.application.match.exception.RoomChatRoomNotFoundException;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

// 판정 순서는 방 안인가(404) → 채팅이 열려 있나(409)다
@ExtendWith(MockitoExtension.class)
@DisplayName("방 채팅 사용 가능 판정 단위 테스트")
class RoomChatAccessPolicyTest {

    private static final long ROOM_ID = 125L;
    private static final UUID USER = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 11, 19, 0);

    @Mock
    private LoadUserStatusPort loadUserStatusPort;

    private RoomChatAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RoomChatAccessPolicy(loadUserStatusPort);
    }

    private void givenStatus(long roomId, RunningRoomType type, RunningRoomStatus roomStatus) {
        given(loadUserStatusPort.loadStatus(new UserId(USER)))
                .willReturn(Optional.of(new UserStatusRow(roomId, type, roomStatus, START, 5_000)));
    }

    @Nested
    @DisplayName("통과 테스트")
    class OpenTest {

        @ParameterizedTest
        @EnumSource(value = RunningRoomType.class, names = {"MATCH", "INVITE"})
        @DisplayName("모집 중인 매칭·초대 방에 배정돼 있으면 통과한다")
        void openWhileMatching(RunningRoomType type) {
            // given
            givenStatus(ROOM_ID, type, RunningRoomStatus.MATCHING);

            // when & then
            assertThatCode(() -> policy.requireOpen(new UserId(USER), ROOM_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("확정 후 대기 중인 방도 통과한다")
        void openWhileMatched() {
            // given
            givenStatus(ROOM_ID, RunningRoomType.MATCH, RunningRoomStatus.MATCHED);

            // when & then
            assertThatCode(() -> policy.requireOpen(new UserId(USER), ROOM_ID)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("방 밖 테스트")
    class NotInRoomTest {

        @Test
        @DisplayName("배정된 방이 없으면 404다")
        void noAssignedRoom() {
            // given -> 나갔거나 처음부터 신청하지 않았다
            given(loadUserStatusPort.loadStatus(new UserId(USER))).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> policy.requireOpen(new UserId(USER), ROOM_ID))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
        }

        @Test
        @DisplayName("다른 방에 배정돼 있으면 404다")
        void assignedToAnotherRoom() {
            // given -> 남의 방이 있는지 없는지를 알려 주지 않는다
            givenStatus(ROOM_ID + 1, RunningRoomType.MATCH, RunningRoomStatus.MATCHING);

            // when & then
            assertThatThrownBy(() -> policy.requireOpen(new UserId(USER), ROOM_ID))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
        }

        @Test
        @DisplayName("솔로 방이면 404다")
        void soloRoom() {
            // given -> 솔로 방에는 채팅이 없다
            givenStatus(ROOM_ID, RunningRoomType.SOLO, RunningRoomStatus.MATCHED);

            // when & then
            assertThatThrownBy(() -> policy.requireOpen(new UserId(USER), ROOM_ID))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
        }

        @Test
        @DisplayName("다른 방에 배정된 사람은 그 방이 시작됐어도 409가 아니라 404다")
        void notInRoomComesBeforeClosed() {
            // given -> 방 밖의 사람에게는 방 상태를 알려 주지 않는다
            givenStatus(ROOM_ID + 1, RunningRoomType.MATCH, RunningRoomStatus.STARTED);

            // when & then
            assertThatThrownBy(() -> policy.requireOpen(new UserId(USER), ROOM_ID))
                    .isInstanceOf(RoomChatRoomNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("채팅 닫힘 테스트")
    class ClosedTest {

        @ParameterizedTest
        @EnumSource(value = RunningRoomStatus.class, names = {"STARTED", "FINISHED", "CANCELLED"})
        @DisplayName("러닝이 시작됐거나 방이 닫혔으면 배정이 남아 있어도 409다")
        void closedRoomIsConflict(RunningRoomStatus roomStatus) {
            // given -> 시작 시각이 지나 아직 RUNNING_START 전인 JOINED도 여기서 막힌다
            givenStatus(ROOM_ID, RunningRoomType.MATCH, roomStatus);

            // when & then
            assertThatThrownBy(() -> policy.requireOpen(new UserId(USER), ROOM_ID))
                    .isInstanceOf(RoomChatClosedException.class);
        }
    }
}
