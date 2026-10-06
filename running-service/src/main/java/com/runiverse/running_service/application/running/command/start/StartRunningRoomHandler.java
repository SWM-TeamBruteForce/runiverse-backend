package com.runiverse.running_service.application.running.command.start;

import com.runiverse.running_service.application.running.port.in.StartRunningRoomUsecase;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 시작 시각 정각 — 확정된 방을 STARTED로 올린다.
// 참가자 JOINED→RUNNING은 여기서 하지 않는다: 앱을 켜지 않은 사람까지 일괄로 올리면
// 상태가 사실과 어긋난다. 각자가 보낸 시작 메시지가 자기 것만 올린다.
// 아무도 붙지 않은 방도 여기서 STARTED가 되므로 뒤이은 종료 예약이 닫을 수 있다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StartRunningRoomHandler implements StartRunningRoomUsecase {

    private final LockRunningRoomPort lockRunningRoomPort;
    private final UpdateRunningRoomPort updateRunningRoomPort;

    @Override
    public void handle(StartRunningRoomCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        // 정각에 도착한 참가자의 시작·이탈이 같은 행을 고친다 — 잠그고 다시 읽어야
        // 상태 판정과 전이 사이에 남이 끼어들지 않는다
        Optional<RunningRoom> locked = lockRunningRoomPort.lockById(roomId);
        if (locked.isEmpty()) {
            // 예약은 남았는데 방이 사라진 경우 — 예약을 소비한 것으로 보고 조용히 끝낸다
            log.warn("[러닝] 방 시작 건너뜀: 방 없음 - roomId={}", roomId.value());
            return;
        }
        RunningRoom room = locked.get();
        // 예약은 인스턴스 여럿이 들고 있고 재시도도 있어 두 번 깰 수 있다.
        // start()는 MATCHED가 아니면 던지므로 이 재확인이 곧 멱등성이고,
        // 먼저 붙은 참가자가 이미 올린 방(STARTED)과 전원이 나간 방(CANCELLED)도 여기서 걸러진다
        if (room.getStatus() != RunningRoomStatus.MATCHED) {
            // 모집 마감 예약이 실패해야만 나오는 상태다. 조용히 지나가면 그 방은
            // 영영 모집 중으로 남아 참가자의 다음 러닝까지 막는다 — 사람이 봐야 한다
            if (room.getStatus() == RunningRoomStatus.MATCHING) {
                log.error("[러닝] 방 시작 실패: 모집 마감 누락 - roomId={}", roomId.value());
            }
            return;
        }
        room.start();
        updateRunningRoomPort.update(room);
    }
}
