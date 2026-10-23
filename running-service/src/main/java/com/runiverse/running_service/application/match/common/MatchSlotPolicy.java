package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.match.exception.InvalidMatchOptionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// 매칭 신청 선택지 판정. 신청 검증과 슬롯 조회가 이 판정을 함께 써야 두 API의 선택지가 갈라지지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchSlotPolicy {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MatchProperties matchProperties;

    // 범위 안에서 첫 시작 시각부터 간격만큼 떨어진 시각만 받는다 — 19:00:30처럼 초가 붙어도 걸린다
    public boolean allowsStartAt(LocalDateTime startAt) {
        LocalTime time = startAt.toLocalTime();
        return !time.isBefore(matchProperties.allowedFirstStartTime())
                && !time.isAfter(matchProperties.allowedLastStartTime())
                && isOnInterval(time);
    }

    public boolean allowsDistance(int distanceMeters) {
        return matchProperties.allowedDistancesMeters().contains(distanceMeters);
    }

    // 모집 마감 전인가 — 신청의 MATCH_SLOT_CLOSED와 슬롯 조회의 selectable이 이 경계를 함께 쓴다.
    // 마감 정각은 이미 마감이다(그 시점에 확정 판정이 돈다). now를 받는 건 조회가 슬롯 전체를 한 시각으로 판정하기 위해서다
    public boolean isOpen(LocalDateTime startAt, LocalDateTime now) {
        return now.isBefore(startAt.minus(matchProperties.closeOffset()));
    }

    // 신청 검증. 둘 다 어긋나면 두 문장을 한 번에 보낸다 — 하나 고치고 다시 보내야 나머지를 알게 하지 않는다
    public void validate(LocalDateTime startAt, int distanceMeters) {
        List<String> violations = new ArrayList<>();
        if (!allowsStartAt(startAt)) {
            violations.add(startTimeRule());
        }
        if (!allowsDistance(distanceMeters)) {
            violations.add(distanceRule());
        }
        if (!violations.isEmpty()) {
            // 사용자가 고른 값의 문제라 INFO다. userId는 MDC가 붙인다
            log.info("[매칭] 신청 실패: 선택지 밖 - startAt={}, distanceM={}", startAt, distanceMeters);
            throw new InvalidMatchOptionException(String.join(" ", violations));
        }
    }

    // 슬롯 조회 검증 — 시각은 조회가 만들어 내려 주는 쪽이라 거리만 본다
    public void validateDistance(int distanceMeters) {
        if (!allowsDistance(distanceMeters)) {
            // 사용자가 고른 값의 문제라 INFO다. userId는 MDC가 붙인다
            log.info("[매칭] 슬롯 조회 실패: 선택지 밖 - distanceM={}", distanceMeters);
            throw new InvalidMatchOptionException(distanceRule());
        }
    }

    // 그날의 신청 가능 시각, 오름차순
    public List<LocalDateTime> startTimesOf(LocalDate date) {
        List<LocalDateTime> startTimes = new ArrayList<>();
        LocalDateTime last = date.atTime(matchProperties.allowedLastStartTime());
        for (LocalDateTime startAt = date.atTime(matchProperties.allowedFirstStartTime());
             !startAt.isAfter(last);
             startAt = startAt.plus(matchProperties.allowedStartTimeInterval())) {
            startTimes.add(startAt);
        }
        return startTimes;
    }

    private boolean isOnInterval(LocalTime time) {
        return Duration.between(matchProperties.allowedFirstStartTime(), time).toNanos()
                % matchProperties.allowedStartTimeInterval().toNanos() == 0;
    }

    // 문구는 운영값으로 만든다 — 설정만 바꾸고 안내가 옛 값을 말하는 일이 없게 한다
    private String startTimeRule() {
        return "시작 시각은 %s부터 %s까지 %d분 간격으로만 선택할 수 있습니다.".formatted(
                matchProperties.allowedFirstStartTime().format(TIME_FORMAT),
                matchProperties.allowedLastStartTime().format(TIME_FORMAT),
                matchProperties.allowedStartTimeInterval().toMinutes());
    }

    private String distanceRule() {
        return "목표 거리는 %s 중 하나여야 합니다.".formatted(
                matchProperties.allowedDistancesMeters().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")));
    }
}
