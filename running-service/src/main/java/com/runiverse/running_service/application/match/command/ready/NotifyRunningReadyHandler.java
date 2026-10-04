package com.runiverse.running_service.application.match.command.ready;

import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.port.in.NotifyRunningReadyUsecase;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RunningReady;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

// 시작 직전 통지 — 방 상태를 바꾸지 않는 유일한 예약이다.
// MATCHED→STARTED는 start_at 정각의 시작 예약이 일으킨다(api-spec 5-C)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotifyRunningReadyHandler implements NotifyRunningReadyUsecase {

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(NotifyRunningReadyCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        // 잠그지 않는다 — 읽고 알리기만 해서 남의 갱신과 다툴 것이 없다
        Optional<RunningRoom> found = loadRunningRoomPort.loadById(roomId);
        if (found.isEmpty()) {
            // 예약은 남았는데 방이 사라진 경우 — 마감 핸들러와 같은 규칙으로 조용히 끝낸다
            log.warn("[매칭] 러닝 준비 통지 건너뜀: 방 없음 - roomId={}", roomId.value());
            return;
        }
        RunningRoom room = found.get();
        // 그새 전원이 나갔거나(CANCELLED) 누군가 먼저 시작했다(STARTED).
        // 취소된 방에 시작을 알리면 클라가 홈으로 갔다가 러닝 화면으로 튄다
        if (room.getStatus() != RunningRoomStatus.MATCHED) {
            return;
        }
        // 받을 사람이 없으면 쏘지 않는다 — 취소·마감 핸들러와 같은 규칙이다
        if (room.getPlayerCount().current() <= 0) {
            return;
        }
        // 예약 시각이 아니라 지금을 기준으로 잰다 — 발화가 밀렸으면 남은 시간도 그만큼 줄어야 한다.
        // 이미 지났으면 0으로 내려 클라가 즉시 쏘게 한다
        long startsInMs = Math.max(0,
                Duration.between(LocalDateTime.now(), room.getStartAt()).toMillis());
        eventPublisher.publishEvent(new MatchRoomChangedEvent(
                MatchStreamEvent.runningReady(new RunningReady(
                        roomId.value(), room.getStartAt(), startsInMs))));
    }
}
