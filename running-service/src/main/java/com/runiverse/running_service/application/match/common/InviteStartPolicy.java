package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.match.exception.InvalidInviteOptionException;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// 초대 방 시각·거리 판정. 매칭과 달리 선택지가 아니라 범위로 막는다
@Slf4j
@Component
public class InviteStartPolicy {

    // 다음 날로 넘기는 예약은 받지 않는다 — 운영값이 아니라 "오늘 하루 안"이라는 규칙이다
    private static final LocalTime LAST_START_TIME = LocalTime.of(23, 59);

    private final InviteProperties inviteProperties;

    // 시작 통지 리드타임은 매칭과 같은 값을 쓰므로 매칭 설정에서 읽어 기동 때 한 번만 맞춰 본다
    public InviteStartPolicy(InviteProperties inviteProperties, MatchProperties matchProperties) {
        // 마감이 통지보다 늦으면 그 사이에 들어온 사람은 RUNNING_READY를 받지 못해 카운트다운을 걸 수 없다
        if (inviteProperties.closeOffset().compareTo(matchProperties.readyOffset()) <= 0) {
            throw new IllegalStateException("invite.close-offset은 match.ready-offset보다 커야 한다");
        }
        // 같거나 길면 가장 이른 시각으로 연 방은 리마인더 시각이 이미 지나 있어 열자마자 나간다
        if (matchProperties.reminderOffset().compareTo(inviteProperties.minLeadTime()) >= 0) {
            throw new IllegalStateException("match.reminder-offset은 invite.min-lead-time보다 작아야 한다");
        }
        this.inviteProperties = inviteProperties;
    }

    // 요청 값 검증. 둘 다 어긋나면 두 문장을 한 번에 보낸다 — 하나 고치고 다시 보내야 나머지를 알게 하지 않는다
    public void validate(LocalDateTime startAt, int distanceMeters) {
        List<String> violations = new ArrayList<>();
        if (!isOnMinute(startAt)) {
            violations.add("시작 시각은 분 단위로만 선택할 수 있습니다.");
        }
        if (!allowsDistance(distanceMeters)) {
            violations.add(distanceRule());
        }
        if (!violations.isEmpty()) {
            // 사용자가 입력한 값의 문제라 INFO다. userId는 MDC가 붙인다
            log.info("[매칭] 초대 방 개설 실패: 입력값 범위 밖 - startAt={}, distanceM={}", startAt, distanceMeters);
            throw new InvalidInviteOptionException(String.join(" ", violations));
        }
    }

    // 지금 시각에 따라 갈리는 판정이라 validate와 나눈다 — 이쪽은 409다.
    // 양 끝을 포함한다. now를 받는 건 테스트가 시각을 고정하기 위해서다
    public boolean isSelectable(LocalDateTime startAt, LocalDateTime now) {
        LocalDateTime earliest = now.plus(inviteProperties.minLeadTime());
        LocalDateTime latest = now.toLocalDate().atTime(LAST_START_TIME);
        return !startAt.isBefore(earliest) && !startAt.isAfter(latest);
    }

    // 모집 마감 = start_at - 초대 마감 오프셋. 컬럼에 저장하지 않는다 — 매칭과 같은 이유다
    public LocalDateTime closeAt(LocalDateTime startAt) {
        return startAt.minus(inviteProperties.closeOffset());
    }

    // 초대·수락을 받는가 — 모집 중이고 마감 전이어야 한다. 마감 시각 자체는 이미 닫힌 것으로 본다.
    // 마감 예약(MATCH_CLOSE)이 아직 안 돌아 MATCHING으로 남아 있어도 시각이 지났으면 닫힌 것이다
    public boolean isRecruiting(RunningRoom room, LocalDateTime now) {
        return room.getStatus() == RunningRoomStatus.MATCHING
                && now.isBefore(closeAt(room.getStartAt()));
    }

    private boolean allowsDistance(int distanceMeters) {
        return distanceMeters >= inviteProperties.minDistanceMeters()
                && distanceMeters <= inviteProperties.maxDistanceMeters();
    }

    private static boolean isOnMinute(LocalDateTime startAt) {
        return startAt.getSecond() == 0 && startAt.getNano() == 0;
    }

    // 문구는 운영값으로 만든다 — 설정만 바꾸고 안내가 옛 값을 말하는 일이 없게 한다
    private String distanceRule() {
        return "목표 거리는 %dm 이상 %dm 이하여야 합니다.".formatted(
                inviteProperties.minDistanceMeters(), inviteProperties.maxDistanceMeters());
    }
}
