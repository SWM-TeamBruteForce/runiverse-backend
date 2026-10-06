package com.runiverse.running_service.application.match.command.close;

import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.in.CloseMatchingUsecase;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 모집 마감 — MATCHING 방을 인원과 무관하게 MATCHED로 굳힌다.
// 1인도 확정이라 인원을 보지 않고, 참가자 status는 건드리지 않는다 —
// JOINED→RUNNING은 각자의 RUNNING_START가 올린다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CloseMatchingHandler implements CloseMatchingUsecase {

    private final LockMatchRoomPort lockMatchRoomPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final RoomInfoAssembler roomInfoAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(CloseMatchingCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        // 신청 배정이 같은 방을 놓고 경합한다 — 확정과 합류 중 하나만 이겨야 한다.
        // 확정이 먼저면 배정은 canJoin()에서 막혀 다음 후보로 넘어간다
        Optional<RunningRoom> locked = lockMatchRoomPort.lockById(roomId);
        if (locked.isEmpty()) {
            // 예약은 남았는데 방이 사라진 경우 — 예약을 소비한 것으로 보고 조용히 끝낸다
            log.warn("[매칭] 모집 마감 건너뜀: 방 없음 - roomId={}", roomId.value());
            return;
        }
        RunningRoom room = locked.get();
        // 예약은 인스턴스 여럿이 들고 있고 재시도도 있어 두 번 깰 수 있다.
        // closeMatching()은 MATCHING이 아니면 던지므로 이 재확인이 곧 멱등성이고,
        // 사용자가 전원 취소해 CANCELLED가 된 방도 여기서 걸러진다
        if (room.getStatus() != RunningRoomStatus.MATCHING) {
            return;
        }
        room.closeMatching();
        updateMatchRoomPort.update(room);
        // 확정된 그 순간에만 STARTED를 쓴다 — 재연결 스냅샷은 UPDATED라
        // 확정 연출이 반복되지 않는다.
        // 빈 방은 leave()가 이미 CANCELLED로 닫아 여기 오지 않지만,
        // 받을 사람이 없으면 쏘지 않는 규칙은 취소 핸들러와 맞춘다
        if (room.getPlayerCount().current() > 0) {
            eventPublisher.publishEvent(new MatchRoomChangedEvent(
                    MatchStreamEvent.started(roomInfoAssembler.assemble(room))));
        }
    }
}
