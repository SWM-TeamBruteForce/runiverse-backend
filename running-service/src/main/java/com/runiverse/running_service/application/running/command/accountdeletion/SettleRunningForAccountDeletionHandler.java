package com.runiverse.running_service.application.running.command.accountdeletion;

import com.runiverse.running_service.application.match.command.stream.MatchStreamCloseRequestedEvent;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.session.RunningConnectionCloseRequestedEvent;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.in.SettleRunningForAccountDeletionUsecase;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class SettleRunningForAccountDeletionHandler
        implements SettleRunningForAccountDeletionUsecase {

    // 확정 인원이 1이면 안 나타나도 곤란해지는 상대가 없다 — 강제 종료의 면제 기준과 같다
    private static final int PENALTY_MIN_PLAYER_COUNT = 2;

    private final LockMatchApplicationPort lockMatchApplicationPort;
    private final LoadMatchRoomPort loadMatchRoomPort;
    private final LockRunningRoomPort lockRunningRoomPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final UpdateRunningPlayerPort updateRunningPlayerPort;
    private final StartMatchCooldownPort startMatchCooldownPort;
    private final FinishRunningUsecase finishRunningUsecase;
    private final RoomInfoAssembler roomInfoAssembler;
    private final MatchProperties matchProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(SettleRunningForAccountDeletionCommand command) {
        UserId userId = new UserId(command.userId());
        // 연결은 커밋 뒤에 닫는다 — 여기서 닫으면 탈퇴가 롤백돼도 되살릴 수 없다
        eventPublisher.publishEvent(new MatchStreamCloseRequestedEvent(userId));
        eventPublisher.publishEvent(new RunningConnectionCloseRequestedEvent(userId));
        // 활성 신청이 없으면 정리할 러닝이 없다 — 탈퇴자 대부분이 여기서 끝난다
        RunningPlayer player = lockMatchApplicationPort.lockActive(userId).orElse(null);
        if (player == null) {
            return;
        }
        // "방 미배정" 상태는 없다 — 비어 있으면 데이터 사고라 드러낸다
        RunningRoomId roomId = loadMatchRoomPort.findAssignedRoom(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "활성 신청에 배정된 방이 없다 — userId=" + userId.value()));
        // 인원이 함께 줄어드니 잠그고 읽는다. 취소·시작과 같은 순서다
        RunningRoom room = lockRunningRoomPort.lockById(roomId)
                .orElseThrow(() -> new IllegalStateException(
                        "배정된 방을 찾을 수 없다 — runningRoomId=" + roomId.value()));

        if (room.getStatus().isBeforeStart()) {
            leaveBeforeStart(userId, player, room);
            return;
        }
        // 방이 시작됐다고 다 뛴 것은 아니다 — 앱을 한 번도 켜지 않으면 JOINED로 남는다
        if (player.getStatus() == RunningPlayerStatus.JOINED) {
            leaveWithoutRunning(userId, player, room);
            return;
        }
        // 살아 있는 신청은 JOINED 아니면 RUNNING이다 — 그 밖이면 데이터 사고라 드러낸다
        if (player.getStatus() != RunningPlayerStatus.RUNNING) {
            throw new IllegalStateException(
                    "활성 신청이 뛸 수 없는 상태다 — userId=" + userId.value()
                            + ", status=" + player.getStatus());
        }
        // 기존 종료 경로가 마지막 좌표까지로 기록을 확정한다. 신청·세션 행은 남긴다
        finishRunningUsecase.handle(
                new FinishRunningCommand(roomId.value(), command.userId(), true));
    }

    // 강제 종료가 유예 뒤에 할 일을 앞당겨 한다 — 탈퇴 시점 때문에 판정이 달라지면 안 되므로
    // 제재 조건도 그쪽과 같다
    private void leaveWithoutRunning(UserId userId, RunningPlayer player, RunningRoom room) {
        boolean penalty = room.getType() == RunningRoomType.MATCH
                && room.getPlayerCount().current() >= PENALTY_MIN_PLAYER_COUNT;
        player.leave(penalty, LocalDateTime.now());
        updateRunningPlayerPort.update(player);
        if (penalty) {
            startMatchCooldownPort.start(userId, matchProperties.cooldown());
        }
        // 인원은 줄이지 않는다. 방도 닫지 않는다 — 강제 종료가 기록 유무로 판정한다
        room.finishSession(userId);
        updateMatchRoomPort.update(room);
    }

    // 일반 취소처럼 닫는다 — 신청은 지우지 않고 통계로 남긴다(erd).
    // 사유도 취소와 같게 남기지만 쿨다운은 걸지 않는다 — 막을 다음 신청이 없다
    private void leaveBeforeStart(UserId userId, RunningPlayer player, RunningRoom room) {
        LocalDateTime now = LocalDateTime.now();
        player.leave(isCancelPenalty(room, now), now);
        updateRunningPlayerPort.update(player);
        // 인원을 줄이고, 0이 되면 방이 CANCELLED로 닫힌다
        room.leave(userId, now);
        updateMatchRoomPort.update(room);
        // 남은 참가자에게 알린다. 인원이 0이면 받을 사람이 없다
        if (room.getPlayerCount().current() > 0) {
            eventPublisher.publishEvent(new MatchRoomChangedEvent(
                    MatchStreamEvent.updated(roomInfoAssembler.assemble(room))));
        }
    }

    // 매칭 취소(CancelMatchHandler)와 같은 기준이다 — 마감이 지난 2인 이상 매칭 방을 깨면 제재 대상이다
    private boolean isCancelPenalty(RunningRoom room, LocalDateTime now) {
        return room.getType() == RunningRoomType.MATCH
                && !now.isBefore(room.getStartAt().minus(matchProperties.closeOffset()))
                && room.getPlayerCount().current() >= PENALTY_MIN_PLAYER_COUNT;
    }
}
