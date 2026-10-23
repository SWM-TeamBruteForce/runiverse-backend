package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.command.combo.RunningComboEvaluator;
import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.port.out.CreateRunningCombosPort;
import com.runiverse.running_service.application.running.port.out.LoadRecordedRoutesPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboHistoryPort;
import com.runiverse.running_service.application.running.port.out.RunningComboHistory;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.combo.RunningCombo;
import com.runiverse.running_service.domain.running.combo.vo.ComboPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 방 하나의 콤보를 Redis에서 running_combos로 옮긴다.
// 방 닫힘이 커밋된 뒤에 불리므로 자기 트랜잭션을 새로 연다 — 커밋 뒤 콜백 안에서는 끝난 트랜잭션에 낄 수 없다
@Component
@RequiredArgsConstructor
public class RunningComboWriter {

    private final LoadRunningComboHistoryPort loadRunningComboHistoryPort;
    private final LoadRecordedRoutesPort loadRecordedRoutesPort;
    private final CreateRunningCombosPort createRunningCombosPort;
    private final RunningComboProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunningComboArchive write(Long runningRoomId) {
        RunningComboHistory history = loadRunningComboHistoryPort.loadHistory(runningRoomId);
        // 이어지던 관계는 쌓인 시각에서 닫는다 — 러닝 중 끊길 때와 같은 규칙이다
        List<RunningComboSegment> closedOnFinish = history.openPairs().stream()
                .map(pair -> RunningComboEvaluator.segmentOf(pair, properties.tick()))
                .flatMap(Optional::stream)
                .toList();
        List<RunningComboSegment> segments =
                distinct(Stream.concat(history.closedSegments().stream(), closedOnFinish.stream()));
        if (segments.isEmpty()) {
            return RunningComboArchive.empty();   // 아무와도 함께 달리지 않은 방 — 경로를 읽을 이유도 없다
        }
        Map<UUID, RouteTimeline> timelines = loadRecordedRoutesPort.loadRoutes(runningRoomId).stream()
                .map(RouteTimeline::of)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(RouteTimeline::userId, Function.identity()));
        List<RunningCombo> combos = toCombos(runningRoomId, segments, timelines);
        createRunningCombosPort.createAll(combos);
        return new RunningComboArchive(history.closedSegments().size(), closedOnFinish.size(), combos.size());
    }

    // 구간을 저장한 뒤 관계를 못 지운 채 방이 닫히면 같은 구간이 끊긴 쪽과 이어지던 쪽에 함께 남는다 —
    // 쌍·시작 시각이 같으면 늦게 끝난 하나만 둔다
    private static List<RunningComboSegment> distinct(Stream<RunningComboSegment> segments) {
        Map<List<Object>, RunningComboSegment> byStart = segments.collect(Collectors.toMap(
                segment -> List.of(segment.first(), segment.second(), segment.startAt()),
                Function.identity(),
                (kept, other) -> other.endAt().isAfter(kept.endAt()) ? other : kept,
                LinkedHashMap::new));
        return List.copyOf(byStart.values());
    }

    // 구간 번호는 쌍마다 시작 시각 순으로 1부터 매긴다
    private List<RunningCombo> toCombos(Long runningRoomId, List<RunningComboSegment> segments,
                                        Map<UUID, RouteTimeline> timelines) {
        Map<List<UserId>, List<RunningComboSegment>> byPair = segments.stream()
                .collect(Collectors.groupingBy(segment -> List.of(segment.first(), segment.second()),
                        LinkedHashMap::new, Collectors.toList()));
        List<RunningCombo> combos = new ArrayList<>();
        for (List<RunningComboSegment> pairSegments : byPair.values()) {
            List<RunningComboSegment> ordered = pairSegments.stream()
                    .sorted(Comparator.comparing(RunningComboSegment::startAt))
                    .toList();
            for (int i = 0; i < ordered.size(); i++) {
                combos.add(toCombo(runningRoomId, ordered.get(i), i + 1, timelines));
            }
        }
        return combos;
    }

    private RunningCombo toCombo(Long runningRoomId, RunningComboSegment segment, int segmentNumber,
                                 Map<UUID, RouteTimeline> timelines) {
        LocalDateTime startAt = toLocal(segment.startAt());
        return RunningCombo.archive()
                .runningRoomId(runningRoomId)
                .firstUserId(segment.first().value())
                .secondUserId(segment.second().value())
                .segmentNumber(segmentNumber)
                .comboCount(segment.comboCount())
                .startAt(startAt)
                .endAt(toLocal(segment.endAt()))
                .firstRoute(achievements(timelines.get(segment.first().value()), startAt, segment.comboCount()))
                .secondRoute(achievements(timelines.get(segment.second().value()), startAt, segment.comboCount()))
                .build();
    }

    // i콤보는 시작 + i × 1회 길이에 달성했다 — 러닝 중 화면 숫자가 i로 바뀐 순간이다.
    // 기록이 없는 참가자는 찍을 경로가 없어 비운다
    private List<ComboPoint> achievements(RouteTimeline timeline, LocalDateTime startAt, int comboCount) {
        if (timeline == null) {
            return null;
        }
        List<ComboPoint> points = new ArrayList<>(comboCount);
        for (int i = 1; i <= comboCount; i++) {
            points.add(timeline.nearest(startAt.plus(properties.tick().multipliedBy(i))));
        }
        return points;
    }

    // 기록 시각은 단말 시각을 서버 기본 시간대로 옮긴 LocalDateTime이다 — 같은 기준으로 맞춘다
    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
