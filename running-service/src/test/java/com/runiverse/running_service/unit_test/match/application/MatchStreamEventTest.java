package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.RunningReady;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 방 상태 이벤트는 방 종류로 이름이 갈린다 — 매칭 방은 MATCH_*, 초대 방은 INVITE_ROOM_*만 받는다
@DisplayName("매칭 스트림 이벤트 단위 테스트")
class MatchStreamEventTest {

    private static final long ROOM_ID = 130L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 27, 19, 0);

    private static RoomInfo roomOf(RunningRoomType type) {
        return new RoomInfo(ROOM_ID, type, RunningRoomStatus.MATCHING, START, START.minusMinutes(1),
                7_000, 330, List.of(), List.of());
    }

    @Nested
    @DisplayName("갱신 테스트")
    class UpdatedTest {

        @Test
        @DisplayName("매칭 방 갱신은 MATCH_ROOM_UPDATED다")
        void matchRoomUpdated() {
            // when
            MatchStreamEvent event = MatchStreamEvent.updated(roomOf(RunningRoomType.MATCH));

            // then
            assertThat(event.type()).isEqualTo(MatchEventType.MATCH_ROOM_UPDATED);
            assertThat(event.runningRoomId()).isEqualTo(ROOM_ID);
            assertThat(event.startsInMs()).isNull();
        }

        @Test
        @DisplayName("초대 방 갱신은 INVITE_ROOM_UPDATED이고 남은 시간을 싣지 않는다")
        void inviteRoomUpdated() {
            // when -> 초대·수락·거절·나가기·마감이 모두 이 이벤트다
            MatchStreamEvent event = MatchStreamEvent.updated(roomOf(RunningRoomType.INVITE));

            // then
            assertThat(event.type()).isEqualTo(MatchEventType.INVITE_ROOM_UPDATED);
            assertThat(event.startsInMs()).isNull();
        }
    }

    @Nested
    @DisplayName("연결 직후 스냅샷 테스트")
    class SnapshotTest {

        @Test
        @DisplayName("매칭 방 스냅샷은 갱신과 같은 MATCH_ROOM_UPDATED다")
        void matchRoomSnapshot() {
            // when
            MatchStreamEvent event = MatchStreamEvent.snapshot(roomOf(RunningRoomType.MATCH), START.minusHours(1));

            // then -> 매칭 방 계약은 그대로다
            assertThat(event.type()).isEqualTo(MatchEventType.MATCH_ROOM_UPDATED);
            assertThat(event.startsInMs()).isNull();
        }

        @Test
        @DisplayName("초대 방 스냅샷은 INVITE_ROOM_ENTRANCE이고 보낸 시점 기준 남은 밀리초를 싣는다")
        void inviteRoomEntrance() {
            // when
            MatchStreamEvent event = MatchStreamEvent.snapshot(
                    roomOf(RunningRoomType.INVITE), START.minusMinutes(19).minusSeconds(45));

            // then -> 19분 45초
            assertThat(event.type()).isEqualTo(MatchEventType.INVITE_ROOM_ENTRANCE);
            assertThat(event.startsInMs()).isEqualTo(1_185_000L);
            assertThat(event.room().runningRoomId()).isEqualTo(ROOM_ID);
        }

        @Test
        @DisplayName("시작 시각이 지났으면 남은 시간은 0이다")
        void pastStartIsZero() {
            // when -> 시작 뒤 재연결
            MatchStreamEvent event = MatchStreamEvent.snapshot(roomOf(RunningRoomType.INVITE), START.plusMinutes(3));

            // then -> 음수를 내려보내지 않는다
            assertThat(event.startsInMs()).isZero();
        }
    }

    @Test
    @DisplayName("확정 통지와 시작 통지는 남은 시간 필드를 쓰지 않는다")
    void startedAndReadyCarryNoEntranceTime() {
        // when
        MatchStreamEvent started = MatchStreamEvent.started(roomOf(RunningRoomType.MATCH));
        MatchStreamEvent ready = MatchStreamEvent.runningReady(new RunningReady(ROOM_ID, START, 10_000L));

        // then -> RUNNING_READY의 남은 시간은 ready 페이로드가 갖는다
        assertThat(started.type()).isEqualTo(MatchEventType.MATCH_STARTED);
        assertThat(started.startsInMs()).isNull();
        assertThat(ready.type()).isEqualTo(MatchEventType.RUNNING_READY);
        assertThat(ready.startsInMs()).isNull();
        assertThat(ready.ready().startsInMs()).isEqualTo(10_000L);
    }
}
