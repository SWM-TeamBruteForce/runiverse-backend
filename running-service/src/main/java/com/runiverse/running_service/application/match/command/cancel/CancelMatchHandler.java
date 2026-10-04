package com.runiverse.running_service.application.match.command.cancel;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.exception.MatchAlreadyStartedException;
import com.runiverse.running_service.application.match.port.in.CancelMatchUsecase;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.UpdateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
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
public class CancelMatchHandler implements CancelMatchUsecase {

    // 확정된 방을 깨는 이탈만 제재한다 — 혼자 남은 방을 나가는 데는 손해를 보는 상대가 없다
    private static final int PENALTY_MIN_PLAYER_COUNT = 2;

    private final LockMatchApplicationPort lockMatchApplicationPort;
    private final LoadMatchRoomPort loadMatchRoomPort;
    private final LockMatchRoomPort lockMatchRoomPort;
    private final UpdateMatchApplicationPort updateMatchApplicationPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final MatchCooldownPort matchCooldownPort;
    private final MatchProperties matchProperties;
    private final RoomInfoAssembler roomInfoAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(CancelMatchCommand command) {
        UserId userId = new UserId(command.userId());
        // 1. 취소할 신청이 없다
        RunningPlayer player = lockMatchApplicationPort.lockActive(userId)
                .orElseThrow(ActiveMatchNotFoundException::new);
        // 2. 러닝이 시작된 뒤에는 이 버튼을 쓰지 않는다 — 여기서 끊으면 WS 종료 경로를 건너뛰어
        //    GPS 트랙과 기록이 저장되지 않은 채 신청만 끝난다
        if (player.getStatus() == RunningPlayerStatus.RUNNING) {
            throw new MatchAlreadyStartedException();
        }
        // 3. "방 미배정" 상태는 없다(feature-spec) — 비어 있으면 데이터 사고라 드러낸다
        RunningRoomId roomId = loadMatchRoomPort.findAssignedRoom(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "활성 신청에 배정된 방이 없다 — userId=" + userId.value()));
        // 4. 인원이 함께 줄어드니 잠그고 읽는다 — 동시 이탈이 겹치면 인원이 어긋난다
        RunningRoom room = lockMatchRoomPort.lockById(roomId)
                .orElseThrow(() -> new IllegalStateException(
                        "배정된 방을 찾을 수 없다 — runningRoomId=" + roomId.value()));

        LocalDateTime now = LocalDateTime.now();
        // 5. 대기 취소든 확정 후 이탈이든 신청이 끝난 사유를 status에 남긴다 — 제재 여부만 갈린다.
        //    마감 전이면 isPenalty가 false라 자연히 대기 취소(MATCHED_LEFT_NO_PENALTY)가 된다
        boolean penalty = isPenalty(room, now);
        player.leave(penalty, now);
        if (penalty) {
            // 근거는 status에 남고, "지금 막혀 있나"는 Redis TTL이 답한다
            matchCooldownPort.start(userId, matchProperties.cooldown());
        }
        updateMatchApplicationPort.update(player);
        // 6. 시작 후에는 인원을 줄이지 않는다 — current_player_count는 확정 인원으로 고정되고,
        //    방은 강제 종료가 기록 유무로 닫는다. 탈퇴 정산의 미출석 처리와 같다
        if (!room.getStatus().isBeforeStart()) {
            room.finishSession(userId);
            updateMatchRoomPort.update(room);
            return;
        }
        // 7. 세션을 끊고 인원을 줄인다. 0이 되면 방이 CANCELLED로 닫힌다
        room.leave(userId, now);
        updateMatchRoomPort.update(room);
        // 8. 남은 참가자에게 알린다. 나간 본인은 곧 스트림을 닫으므로 대상이 아니다.
        // 인원이 0이면 받을 사람이 없어 발행하지 않는다
        if (room.getPlayerCount().current() > 0) {
            eventPublisher.publishEvent(new MatchRoomChangedEvent(
                    MatchStreamEvent.updated(roomInfoAssembler.assemble(room))));
        }
    }

    // 마감·인원·방 종류가 함께 걸린다 — 혼자 남은 방을 나가는 데는 손해를 보는 상대가 없고(feature-spec),
    // 마감 전이면 아직 확정되지 않아 깰 약속도 없다
    private boolean isPenalty(RunningRoom room, LocalDateTime now) {
        return room.getType() == RunningRoomType.MATCH
                && !now.isBefore(closeAt(room))
                && room.getPlayerCount().current() >= PENALTY_MIN_PLAYER_COUNT;
    }

    private LocalDateTime closeAt(RunningRoom room) {
        return room.getStartAt().minus(matchProperties.closeOffset());
    }
}
