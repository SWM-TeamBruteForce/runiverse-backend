package com.runiverse.running_service.integration_test.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.exception.InvalidMatchOptionException;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsHandler;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult.MatchSlot;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryMatchStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("슬롯 조회 통합 테스트")
public class GetMatchSlotsIntegrationTest extends IntegrationTestSupport {

    private static final int TARGET_DISTANCE = 5_000;
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDateTime SLOT_19 = TOMORROW.atTime(19, 0);
    private static final LocalDateTime SLOT_20 = TOMORROW.atTime(20, 0);

    // 운영 설정과 같은 값
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6), 10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));

    private GetMatchSlotsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetMatchSlotsHandler(
                new MatchSlotPolicy(MATCH_PROPERTIES),
                new InMemoryMatchStore(runningStore)   // CountWaitingPlayersPort
        );
    }

    @Test
    @DisplayName("같은 슬롯·거리에서 모집 중인 방들의 인원을 합산한다")
    void sumsWaitingPlayersAcrossRooms() {
        // given -> 19:00에 방 둘(2명·1명), 20:00에 방 하나(4명)
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, TARGET_DISTANCE, 2);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, TARGET_DISTANCE, 1);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_20, TARGET_DISTANCE, 4);

        // when
        GetMatchSlotsResult result = handler.handle(new GetMatchSlotsQuery(TOMORROW, TARGET_DISTANCE));

        // then -> 9개 슬롯 중 19:00(index 2)·20:00(index 4)만 사람이 있다
        assertThat(result.slots()).extracting(MatchSlot::waitingCount)
                .containsExactly(0, 0, 3, 0, 4, 0, 0, 0, 0);
        assertThat(result.slots()).allMatch(MatchSlot::selectable);
    }

    @Test
    @DisplayName("확정된 방·솔로 방·다른 거리·다른 날짜는 대기 인원에 넣지 않는다")
    void excludesRoomsThatAreNotOpenSeats() {
        // given -> 19:00에 모집 중인 1명만 남아야 한다
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, TARGET_DISTANCE, 1);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHED, SLOT_19, TARGET_DISTANCE, 3);
        givenRoom(RunningRoomType.SOLO, RunningRoomStatus.STARTED, SLOT_19, TARGET_DISTANCE, 1);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, 10_000, 2);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19.plusDays(1), TARGET_DISTANCE, 2);

        // when
        GetMatchSlotsResult result = handler.handle(new GetMatchSlotsQuery(TOMORROW, TARGET_DISTANCE));

        // then
        assertThat(result.slots()).extracting(MatchSlot::waitingCount)
                .containsExactly(0, 0, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("목록 밖 거리는 거절한다")
    void rejectsUnlistedDistance() {
        // when & then
        assertThatThrownBy(() -> handler.handle(new GetMatchSlotsQuery(TOMORROW, 7_000)))
                .isInstanceOf(InvalidMatchOptionException.class)
                .hasMessage("목표 거리는 3000, 5000, 10000 중 하나여야 합니다.");
    }

    // 인원 수만큼 접속 중인 참가자를 실은 방을 직접 둔다
    private void givenRoom(RunningRoomType type, RunningRoomStatus status, LocalDateTime startAt,
                           int targetDistance, int playerCount) {
        List<SessionDraft> sessions = new ArrayList<>();
        for (int seat = 0; seat < playerCount; seat++) {
            UUID userId = UuidCreator.getTimeOrderedEpoch();
            RunningPlayer player = runningStore.create(
                    RunningPlayer.request(userId, 360, targetDistance, startAt));
            sessions.add(new SessionDraft(new UserId(userId), player.getRunningPlayerId().orElseThrow(), 0, true));
        }
        runningStore.create(RunningRoom.builder()
                .type(type)
                .status(status)
                .startAt(startAt)
                .targetDistance(targetDistance)
                .avgPace(360)
                .currentPlayerCount(playerCount)
                .maxPlayerCount(type == RunningRoomType.SOLO ? 1 : 4)
                .sessions(sessions)
                .build());
    }
}
