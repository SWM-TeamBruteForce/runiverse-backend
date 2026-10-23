package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.exception.InvalidMatchOptionException;
import com.runiverse.running_service.application.match.port.out.CountWaitingPlayersPort;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsHandler;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsQuery;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult;
import com.runiverse.running_service.application.match.query.slot.GetMatchSlotsResult.MatchSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

// 선택지는 실제 정책으로 만든다 — 슬롯 목록과 신청 검증이 같은 판정을 쓰는지가 이 조회의 요점이다
@ExtendWith(MockitoExtension.class)
@DisplayName("슬롯 조회 단위 테스트")
class GetMatchSlotsHandlerTest {

    private static final int DISTANCE = 5_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(10);
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);

    @Mock
    private CountWaitingPlayersPort countWaitingPlayersPort;

    @Test
    @DisplayName("그날의 슬롯을 대기자가 없어도 빠짐없이 오름차순으로 돌려준다")
    void listsEverySlotInOrder() {
        // given -> 대기자가 있는 시각만 포트가 돌려준다
        given(countWaitingPlayersPort.countWaitingPlayers(TOMORROW, DISTANCE))
                .willReturn(Map.of(TOMORROW.atTime(18, 30), 3, TOMORROW.atTime(19, 0), 1));

        // when
        GetMatchSlotsResult result = handler(CLOSE_OFFSET).handle(new GetMatchSlotsQuery(TOMORROW, DISTANCE));

        // then -> 18:00~22:00 30분 간격 9개, 비어 있는 시각은 0
        assertThat(result.slots()).extracting(MatchSlot::scheduledStartAt).containsExactly(
                TOMORROW.atTime(18, 0), TOMORROW.atTime(18, 30), TOMORROW.atTime(19, 0),
                TOMORROW.atTime(19, 30), TOMORROW.atTime(20, 0), TOMORROW.atTime(20, 30),
                TOMORROW.atTime(21, 0), TOMORROW.atTime(21, 30), TOMORROW.atTime(22, 0));
        assertThat(result.slots()).extracting(MatchSlot::waitingCount)
                .containsExactly(0, 3, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("마감 전 슬롯은 고를 수 있다")
    void futureSlotsAreSelectable() {
        // given
        given(countWaitingPlayersPort.countWaitingPlayers(TOMORROW, DISTANCE)).willReturn(Map.of());

        // when
        GetMatchSlotsResult result = handler(CLOSE_OFFSET).handle(new GetMatchSlotsQuery(TOMORROW, DISTANCE));

        // then
        assertThat(result.slots()).allMatch(MatchSlot::selectable);
    }

    @Test
    @DisplayName("마감이 지난 슬롯도 목록에 남기되 고를 수 없다")
    void pastSlotsStayListedButNotSelectable() {
        // given
        given(countWaitingPlayersPort.countWaitingPlayers(YESTERDAY, DISTANCE)).willReturn(Map.of());

        // when
        GetMatchSlotsResult result = handler(CLOSE_OFFSET).handle(new GetMatchSlotsQuery(YESTERDAY, DISTANCE));

        // then
        assertThat(result.slots()).hasSize(9).noneMatch(MatchSlot::selectable);
    }

    @Test
    @DisplayName("마감 시각 정각은 이미 마감이다 — 신청의 MATCH_SLOT_CLOSED와 같은 경계")
    void closeTimeItselfIsClosed() {
        // given -> 18:00 슬롯의 마감이 지금이 되도록 오프셋을 맞춘다. 핸들러의 now는 그 뒤라 정각 이후다.
        //          18:30 슬롯은 30분 뒤에 마감한다
        LocalDateTime firstSlot = TOMORROW.atTime(18, 0);
        Duration offsetClosingFirstSlotNow = Duration.between(LocalDateTime.now(), firstSlot);
        given(countWaitingPlayersPort.countWaitingPlayers(TOMORROW, DISTANCE)).willReturn(Map.of());

        // when
        GetMatchSlotsResult result = handler(offsetClosingFirstSlotNow)
                .handle(new GetMatchSlotsQuery(TOMORROW, DISTANCE));

        // then
        assertThat(result.slots().get(0).selectable()).isFalse();
        assertThat(result.slots().get(1).selectable()).isTrue();
    }

    @Test
    @DisplayName("목록 밖 거리는 거절하고 집계하지 않는다")
    void rejectsUnlistedDistanceBeforeCounting() {
        // when & then
        assertThatThrownBy(() -> handler(CLOSE_OFFSET).handle(new GetMatchSlotsQuery(TOMORROW, 7_000)))
                .isInstanceOf(InvalidMatchOptionException.class)
                .hasMessage("목표 거리는 3000, 5000, 10000 중 하나여야 합니다.");
        verifyNoInteractions(countWaitingPlayersPort);
    }

    private GetMatchSlotsHandler handler(Duration closeOffset) {
        MatchProperties properties = new MatchProperties(
                closeOffset, Duration.ofSeconds(10), Duration.ofHours(6), 10, Duration.ofMinutes(20),
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
        return new GetMatchSlotsHandler(new MatchSlotPolicy(properties), countWaitingPlayersPort);
    }
}
