package com.runiverse.running_service.application.running.query.record;

import com.runiverse.running_service.application.recordcard.common.RecordCardStorageKeys;
import com.runiverse.running_service.application.recordcard.common.RouteArtConverter;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardViewUrlPort;
import com.runiverse.running_service.application.running.port.in.GetMyRunningRecordsUsecase;
import com.runiverse.running_service.application.running.port.out.LoadMyRunningRecordsPort;
import com.runiverse.running_service.application.running.port.out.LoadSegmentPolylinesPort;
import com.runiverse.running_service.application.running.port.out.RunningRecordRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyRunningRecordsHandler implements GetMyRunningRecordsUsecase {

    // 달력 칸은 작다 — 선 아트 점은 50개면 충분하다(상세 8-1은 200개)
    private static final int CALENDAR_ROUTE_ART_POINTS = 50;

    private final LoadMyRunningRecordsPort loadMyRunningRecordsPort;
    private final LoadSegmentPolylinesPort loadSegmentPolylinesPort;
    private final GenerateRecordCardViewUrlPort generateRecordCardViewUrlPort;

    @Override
    public GetMyRunningRecordsResult handle(GetMyRunningRecordsQuery query) {
        LocalDateTime startInclusive = query.from().atStartOfDay();
        LocalDateTime endExclusive = query.to().plusDays(1).atStartOfDay();

        List<RunningRecordRow> rows = loadMyRunningRecordsPort.loadByPeriod(
                new UserId(query.userId()), startInclusive, endExclusive);

        // 사진 없는 카드만 선 아트를 그린다 — 그 기록들의 구간을 한 번에 읽고, 없으면 쿼리를 보내지 않는다
        List<Long> routeArtRecordIds = rows.stream()
                .filter(GetMyRunningRecordsHandler::needsRouteArt)
                .map(RunningRecordRow::runningRecordId)
                .toList();
        Map<Long, List<String>> segments = routeArtRecordIds.isEmpty()
                ? Map.of()
                : loadSegmentPolylinesPort.loadSegmentPolylines(routeArtRecordIds);

        return new GetMyRunningRecordsResult(rows.stream()
                .map(row -> toRunningRecord(row, segments))
                .toList());
    }

    private GetMyRunningRecordsResult.RunningRecord toRunningRecord(RunningRecordRow row,
                                                                    Map<Long, List<String>> segments) {
        return new GetMyRunningRecordsResult.RunningRecord(
                row.runningRecordId(),
                row.runningRoomId(),
                row.type(),
                row.playerCount(),
                row.startedAt(),
                row.totalDistanceMeters(),
                row.totalDurationSeconds(),
                row.averagePaceSecondsPerKm(),
                row.totalElevationGainMeters(),
                recordCardOf(row, segments));
    }

    // 사진이 있으면 대표 사진의 작은 버전(썸네일), 없으면 선 아트 — 내 기록이라 공개 범위를 보지 않는다(비공개 카드도 싣는다)
    private GetMyRunningRecordsResult.RecordCard recordCardOf(RunningRecordRow row,
                                                              Map<Long, List<String>> segments) {
        if (row.recordCardId() == null) {
            return null;
        }
        if (row.thumbnailImageKey() != null) {
            String thumbnailKey = RecordCardStorageKeys.thumbnailKeyOf(row.thumbnailImageKey());
            return new GetMyRunningRecordsResult.RecordCard(row.recordCardId(), row.cardActivityType(),
                    generateRecordCardViewUrlPort.imageUrl(thumbnailKey), null);
        }
        return new GetMyRunningRecordsResult.RecordCard(row.recordCardId(), row.cardActivityType(), null,
                RouteArtConverter.convert(segments.getOrDefault(row.runningRecordId(), List.of()),
                        CALENDAR_ROUTE_ART_POINTS));
    }

    // 썸네일은 사진이 있을 때만 있다 — 카드가 있는데 썸네일이 없으면 사진 없는 카드다
    private static boolean needsRouteArt(RunningRecordRow row) {
        return row.recordCardId() != null && row.thumbnailImageKey() == null;
    }
}
