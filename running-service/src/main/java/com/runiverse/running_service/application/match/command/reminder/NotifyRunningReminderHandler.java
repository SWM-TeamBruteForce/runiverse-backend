package com.runiverse.running_service.application.match.command.reminder;

import com.runiverse.running_service.application.device.command.push.PushRequestedEvent;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.port.in.NotifyRunningReminderUsecase;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 시작 전 푸시 — 앱을 닫아 둔 참가자도 시작을 놓치지 않게 한다. 방 상태는 바꾸지 않는다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotifyRunningReminderHandler implements NotifyRunningReminderUsecase {

    private static final String TITLE = "러닝 시작 %d분 전이에요";
    private static final String BODY = "%s에 출발해요. 로비에서 기다려주세요";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long MINUTE_MILLIS = Duration.ofMinutes(1).toMillis();

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(NotifyRunningReminderCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        // 잠그지 않는다 — 읽고 알리기만 해서 남의 갱신과 다툴 것이 없다
        Optional<RunningRoom> found = loadRunningRoomPort.loadById(roomId);
        if (found.isEmpty()) {
            log.warn("[매칭] 러닝 리마인더 건너뜀: 방 없음 - roomId={}", roomId.value());
            return;
        }
        RunningRoom room = found.get();
        // 초대 방은 마감이 시작 직전이라 이때 아직 MATCHING이다 — 확정 여부가 아니라 시작 전인지만 본다
        if (!room.getStatus().isBeforeStart()) {
            log.info("[매칭] 러닝 리마인더 건너뜀: 시작 전이 아님 - roomId={}, status={}",
                    roomId.value(), room.getStatus());
            return;
        }
        // 서버가 멈췄다 복구되면 지난 예약도 바로 돈다 — 시작이 지났으면 알릴 것이 없다
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(room.getStartAt())) {
            log.info("[매칭] 러닝 리마인더 건너뜀: 시작 시각 지남 - roomId={}, startAt={}",
                    roomId.value(), room.getStartAt());
            return;
        }
        // 나간 사람은 빼고 지금 방에 남은 사람만 받는다
        List<UserId> recipients = room.getSessions().stream()
                .filter(RoomSession::isConnected)
                .map(RoomSession::getUserId)
                .toList();
        PushMessage message = reminderOf(roomId, room.getStartAt(), Duration.between(now, room.getStartAt()));
        recipients.forEach(userId -> eventPublisher.publishEvent(new PushRequestedEvent(userId.value(), message)));
        log.info("[매칭] 러닝 리마인더 성공 - roomId={}, recipientCount={}", roomId.value(), recipients.size());
    }

    // 남은 분은 올린다 — 예약은 정시보다 조금 늦게 깨서, 내리면 5분 전 발화가 "4분 전"이 된다.
    // 유효 시간은 시작까지다 — 꺼져 있던 폰이 시작 뒤에 켜져 지난 알림을 받지 않게 한다
    private static PushMessage reminderOf(RunningRoomId roomId, LocalDateTime startAt, Duration untilStart) {
        return new PushMessage(PushType.RUNNING_REMINDER,
                TITLE.formatted(Math.ceilDiv(untilStart.toMillis(), MINUTE_MILLIS)),
                BODY.formatted(startAt.format(TIME_FORMAT)),
                Map.of("runningRoomId", String.valueOf(roomId.value())),
                untilStart);
    }
}
