package com.runiverse.running_service.application.running.command.forcefinish;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.in.ForceFinishRunningRoomUsecase;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LockRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 시작 시각 + 유예 — 그때까지 닫히지 않은 방을 서버가 닫는다.
// 남은 참가자를 상태로 갈라 각자의 정상 경로에 태운다: 뛰던 사람은 러닝 종료 그대로 확정하고,
// 한 번도 붙지 않은 사람은 확정 후 이탈로 판정한다.
// 방이 열린 채로 남으면 참가자의 신청도 활성이라 다음 러닝이 영영 막힌다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ForceFinishRunningRoomHandler implements ForceFinishRunningRoomUsecase {

    // 확정 인원이 1이면 안 나타나도 곤란해지는 상대가 없다 — 시작 전 이탈 면제와 같은 기준이다
    private static final int PENALTY_MIN_PLAYER_COUNT = 2;
    private final LockRunningPlayerPort lockRunningPlayerPort;
    private final LockRunningRoomPort lockRunningRoomPort;
    private final LoadRunningRoomPort loadRunningRoomPort;
    private final LoadRoomPlayerPort loadRoomPlayerPort;
    private final UpdateRunningRoomPort updateRunningRoomPort;
    private final UpdateRunningPlayerPort updateRunningPlayerPort;
    private final StartMatchCooldownPort startMatchCooldownPort;
    private final ExistsRunningRecordPort existsRunningRecordPort;
    private final FinishRunningUsecase finishRunningUsecase;
    private final MatchProperties matchProperties;

    @Override
    public void handle(ForceFinishRunningRoomCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        // 참가자를 방보다 먼저 잠근다 — 시작·취소·탈퇴 정산이 모두 참가자 → 방 순이라,
        // 방을 먼저 잡으면 같은 순간 들어온 RUNNING_START와 서로의 잠금을 기다리다 교착에 빠진다
        lockRunningPlayerPort.lockActiveInRoom(roomId);
        // 남은 참가자의 종료와 같은 행을 고친다 — 잠그고 읽는다
        Optional<RunningRoom> locked = lockRunningRoomPort.lockById(roomId);
        if (locked.isEmpty()) {
            log.warn("[러닝] 강제 종료 건너뜀: 방 없음 - roomId={}", roomId.value());
            return;
        }
        RunningRoom room = locked.get();
        // 방이 이미 닫혔어도 그냥 빠지지 않는다 — 남들이 정상 종료해 방이 FINISHED가 된 뒤에도
        // 한 번도 붙지 않은 참가자는 JOINED인 채로 남아 다음 러닝을 막는다
        //
        // 판정 기준은 루프 전에 굳힌다. 처리하면서 세면 순회 순서에 따라 뒤쪽만 면제받는다
        boolean penalty = isPenalty(room);
        List<UserId> remaining = room.getSessions().stream()
                .filter(RoomSession::isConnected)
                .map(RoomSession::getUserId)
                .toList();
        // 1. 안 나타난 사람부터 닫는다. 러닝 종료가 방을 다시 읽기 전에
        //    이 인스턴스의 수정을 다 반영해야 둘이 서로 덮어쓰지 않는다
        List<UserId> runners = new ArrayList<>();
        for (UserId userId : remaining) {
            RunningPlayer player = loadRoomPlayerPort.load(roomId, userId).orElse(null);
            if (player == null || !player.isActive()) {
                // 신청은 이미 닫혔는데 세션만 남은 경우 — 자리만 비운다
                room.finishSession(userId);
                continue;
            }
            if (player.getStatus() == RunningPlayerStatus.RUNNING) {
                runners.add(userId);   // 2단계의 러닝 종료가 세션까지 정리한다
                continue;
            }
            leaveWithoutShowing(player, penalty);
            room.finishSession(userId);
        }
        // 인원은 줄이지 않는다 — 면제 판정은 순회 전에 굳힌 인원으로 이미 끝났다.
        // leave()를 쓰면 마지막 한 명에서 방이 CANCELLED가 돼 종료 경로가 무너진다
        updateRunningRoomPort.update(room);
        // 2. 뛰던 사람은 평소 종료와 똑같이 확정한다 — 트랙 분석·기록 생성·거리 비율 판정이 그대로 돈다.
        //    마지막 한 명이 끝나는 순간 그 안에서 방이 FINISHED로 닫힌다
        for (UserId userId : runners) {
            finishRunningUsecase.handle(new FinishRunningCommand(
                    roomId.value(), userId.value(), true));
        }
        // 3. 러닝 종료가 방을 닫지 못했으면 여기서 닫는다.
        //    아무도 뛰지 않은 방은 닫아 줄 러닝 종료가 아예 없다
        closeRoomIfStillOpen(roomId);
    }

    // 안 나타난 사람은 확정 후 이탈과 같게 다룬다 — 함께 뛰기로 한 사람을 곤란하게 만든 것은 같다.
    // 상태가 MATCHED_LEFT_*라 쿨다운도 조기 종료가 아니라 이탈 쪽 값을 쓴다
    private void leaveWithoutShowing(RunningPlayer player, boolean penalty) {
        player.leave(penalty, LocalDateTime.now());
        updateRunningPlayerPort.update(player);
        if (penalty) {
            // 근거는 status(MATCHED_LEFT_PENALTY)에 남고, "지금 막혀 있나"는 Redis TTL이 답한다
            startMatchCooldownPort.start(player.getUserId(), matchProperties.cooldown());
        }
    }
    
    // 위에서 들고 있던 방은 러닝 종료가 다시 읽어 고쳤을 수 있다 — 반드시 새로 읽는다.
    // 그 인스턴스로 update를 부르면 방금 닫힌 상태를 되돌린다
    private void closeRoomIfStillOpen(RunningRoomId roomId) {
        RunningRoom room = loadRunningRoomPort.loadById(roomId).orElseThrow();
        if (room.getStatus().isTerminal()) {
            return;   // 마지막 참가자의 러닝 종료가 이미 닫았다
        }
        LocalDateTime closedAt = LocalDateTime.now();
        // 러닝 종료와 같은 규칙이다 — 남길 기록이 있으면 FINISHED, 없으면 CANCELLED
        if (existsRunningRecordPort.existsInRoom(roomId)) {
            room.finish(closedAt);
        } else {
            room.cancel(closedAt);
        }
        updateRunningRoomPort.update(room);
    }

    // 마감 시각은 이미 한참 전이라 조건에서 빠진다 — 방 종류와 확정 인원만 남는다
    private boolean isPenalty(RunningRoom room) {
        return room.getType() == RunningRoomType.MATCH
                && room.getPlayerCount().current() >= PENALTY_MIN_PLAYER_COUNT;
    }


}
