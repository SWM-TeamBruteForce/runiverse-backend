package com.runiverse.running_service.application.running.command.start;

import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningNotStartableException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.port.in.StartRunningUsecase;
import com.runiverse.running_service.application.running.port.out.LoadActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class StartRunningHandler implements StartRunningUsecase {

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final UpdateRunningRoomPort updateRunningRoomPort;
    private final LoadActiveRunningPlayerPort loadActiveRunningPlayerPort;
    private final UpdateRunningPlayerPort updateRunningPlayerPort;

    // 채널 등록·재입장·방 시작·참가자 시작을 한 번에 처리한다.
    // 클라는 최초 진입인지 재연결인지 구분하지 않고 언제나 같은 메시지를 보낸다
    @Override
    public StartRunningResult handle(StartRunningCommand command) {
        // 1. 방을 우선 찾는다.
        RunningRoom room = loadRunningRoomPort.loadById(new RunningRoomId(command.runningRoomId()))
                .orElseThrow(RunningRoomNotFoundException::new);

        // 2. 커맨드에는 userId뿐이라 활성 신청을 읽어 playerId를 얻고,
        //    그 ID가 이 방의 세션에 있는지로 참가자 여부를 판정한다.
        RunningPlayer player = loadActiveRunningPlayerPort.loadActive(new UserId(command.userId()))
                .orElseThrow(NotRoomPlayerException::new);
        RunningPlayerId playerId = player.getRunningPlayerId().orElseThrow();
        RoomSession session = room.getSessions().stream()
                .filter(roomSession -> roomSession.isSamePlayer(playerId))
                .findFirst()
                .orElseThrow(NotRoomPlayerException::new);
        // 도메인 예외는 WS 에러 코드로 매핑하지 않는다 — 거부 사유는 전부 여기서 걸러야 한다.
        // 아래 도메인 호출에서 예외가 나오면 그건 이 검사가 샌 것이고 곧 버그다
        ensureStartable(room); // 추가로 시작 전 방에 들어간것도 여기서 거른다.
        ensureStartable(player);

        // 3. 나갔던 사람만 되살린다. 네트워크가 끊겼다 붙은 것뿐이면 세션은 살아 있어 통과한다
        if (!session.isConnected()) {
            if (!room.getPlayerCount().canJoin()) {
                throw new RunningNotStartableException();   // 그새 자리가 찼다
            }
            room.rejoin(playerId);
        }

        // 4. 이미 STARTED면 재연결이라 아무 일도 일어나지 않는다
        if (room.getStatus() == RunningRoomStatus.MATCHED) {
            room.start();
        }

        // 5. 참가자는 각자 시작한다 — 시작 시각에 앱을 안 켠 사람까지 RUNNING이 되면 안 된다
        if (player.getStatus() == RunningPlayerStatus.JOINED) {
            player.start();
        }

        // 6. 본격적인 업데이트를 시작한다
        updateRunningRoomPort.update(room);
        updateRunningPlayerPort.update(player);
        return new StartRunningResult(
                room.getRunningRoomId().orElseThrow().value(),
                room.getTargetDistance().map(Distance::meters).orElse(null));
    }

    // 러닝을 시작할 수 있는 신청인가 — INVITED는 아직 수락하지 않았고,
    // 그 밖의 값은 끝난 신청인데 deleted_at이 안 찍힌 데이터 이상이다
    private void ensureStartable(RunningPlayer player) {
        if (player.getStatus() != RunningPlayerStatus.JOINED
                && player.getStatus() != RunningPlayerStatus.RUNNING) {
            throw new RunningNotStartableException();
        }
    }

    // 시작 시각 전이면 매칭 클라가 미리 쏜 것이고, 끝난 방이면 되돌릴 수 없다
    private void ensureStartable(RunningRoom room) {
        // 매칭 클라가 시작 시각 전에 쏜 경우
        if (LocalDateTime.now().isBefore(room.getStartAt())) {
            throw new RunningNotStartableException();
        }
        // MATCHING은 스케줄러가 아직 확정하지 않은 방이다 — 여기서 대신 확정해주지 않는다.
        // FINISHED·CANCELLED는 되돌릴 수 없다
        if (room.getStatus() != RunningRoomStatus.MATCHED
                && room.getStatus() != RunningRoomStatus.STARTED) {
            throw new RunningNotStartableException();
        }
    }
}
