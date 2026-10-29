package com.runiverse.running_service.integration_test.match;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsHandler;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult.DistanceSlots;
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

@DisplayName("슬롯 조회 통합 테스트")
public class GetMatchSlotsIntegrationTest extends IntegrationTestSupport {

    private static final int TARGET_DISTANCE = 5_000;
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDateTime SLOT_19 = TOMORROW.atTime(19, 0);
    private static final LocalDateTime SLOT_20 = TOMORROW.atTime(20, 0);

    // 운영 설정과 같은 값
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofHours(6),
            10, Duration.ofMinutes(20),
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
        GetMatchSlotsResult result = handler.handle(new GetMatchSlotsQuery(TOMORROW));

        // then -> 9개 슬롯 중 19:00(index 2)·20:00(index 4)만 사람이 있다
        assertThat(slotsOf(result, TARGET_DISTANCE)).extracting(MatchSlot::waitingCount)
                .containsExactly(0, 0, 3, 0, 4, 0, 0, 0, 0);
        assertThat(slotsOf(result, TARGET_DISTANCE)).allMatch(MatchSlot::selectable);
    }

    @Test
    @DisplayName("같은 시각이어도 거리가 다르면 각 거리에 따로 센다")
    void countsEachDistanceSeparately() {
        // given -> 19:00에 5km 2명, 10km 3명
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, TARGET_DISTANCE, 2);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, 10_000, 3);

        // when
        GetMatchSlotsResult result = handler.handle(new GetMatchSlotsQuery(TOMORROW));

        // then -> 거리는 오름차순, 대기자 없는 3km도 빠지지 않는다
        assertThat(result.distances()).extracting(DistanceSlots::targetDistanceMeters)
                .containsExactly(3_000, 5_000, 10_000);
        assertThat(slotsOf(result, 3_000)).extracting(MatchSlot::waitingCount).containsOnly(0);
        assertThat(slotsOf(result, TARGET_DISTANCE)).extracting(MatchSlot::waitingCount)
                .containsExactly(0, 0, 2, 0, 0, 0, 0, 0, 0);
        assertThat(slotsOf(result, 10_000)).extracting(MatchSlot::waitingCount)
                .containsExactly(0, 0, 3, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("확정된 방·솔로 방·다른 날짜·선택지 밖 거리는 대기 인원에 넣지 않는다")
    void excludesRoomsThatAreNotOpenSeats() {
        // given -> 19:00에 모집 중인 1명만 남아야 한다
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, TARGET_DISTANCE, 1);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHED, SLOT_19, TARGET_DISTANCE, 3);
        givenRoom(RunningRoomType.SOLO, RunningRoomStatus.STARTED, SLOT_19, TARGET_DISTANCE, 1);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19.plusDays(1), TARGET_DISTANCE, 2);
        givenRoom(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, SLOT_19, 7_000, 2);

        // when
        GetMatchSlotsResult result = handler.handle(new GetMatchSlotsQuery(TOMORROW));

        // then
        assertThat(result.distances()).extracting(DistanceSlots::targetDistanceMeters)
                .containsExactly(3_000, 5_000, 10_000);
        assertThat(slotsOf(result, TARGET_DISTANCE)).extracting(MatchSlot::waitingCount)
                .containsExactly(0, 0, 1, 0, 0, 0, 0, 0, 0);
        assertThat(slotsOf(result, 3_000)).extracting(MatchSlot::waitingCount).containsOnly(0);
        assertThat(slotsOf(result, 10_000)).extracting(MatchSlot::waitingCount).containsOnly(0);
    }

    private static List<MatchSlot> slotsOf(GetMatchSlotsResult result, int distance) {
        return result.distances().stream()
                .filter(distanceSlots -> distanceSlots.targetDistanceMeters() == distance)
                .findFirst()
                .orElseThrow()
                .slots();
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
