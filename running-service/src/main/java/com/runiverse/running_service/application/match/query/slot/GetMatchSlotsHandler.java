package com.runiverse.running_service.application.match.query.slot;

import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.port.in.GetMatchSlotsUsecase;
import com.runiverse.running_service.application.match.port.out.CountWaitingPlayersPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMatchSlotsHandler implements GetMatchSlotsUsecase {

    private final MatchSlotPolicy matchSlotPolicy;
    private final CountWaitingPlayersPort countWaitingPlayersPort;

    @Override
    public GetMatchSlotsResult handle(GetMatchSlotsQuery query) {
        // 1. 고를 수 있는 거리인가 — 목록 밖 거리로는 방이 생기지 않아 집계할 대상이 없다
        matchSlotPolicy.validateDistance(query.targetDistanceMeters());
        // 2. 대기자가 있는 시각만 온다 — 없는 시각은 아래에서 0으로 채운다
        Map<LocalDateTime, Integer> waitingCounts =
                countWaitingPlayersPort.countWaitingPlayers(query.date(), query.targetDistanceMeters());
        // 3. 마감 판정은 한 시각으로 한다 — 슬롯마다 now()를 부르면 경계에서 이웃 슬롯이 서로 다른 시각으로 판정된다.
        //    경계는 신청의 MATCH_SLOT_CLOSED와 같아야 해 정책이 함께 판정한다
        LocalDateTime now = LocalDateTime.now();
        return new GetMatchSlotsResult(matchSlotPolicy.startTimesOf(query.date()).stream()
                .map(startAt -> new GetMatchSlotsResult.MatchSlot(
                        startAt,
                        waitingCounts.getOrDefault(startAt, 0),
                        matchSlotPolicy.isOpen(startAt, now)))
                .toList());
    }
}
