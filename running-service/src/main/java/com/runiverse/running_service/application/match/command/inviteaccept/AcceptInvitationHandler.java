package com.runiverse.running_service.application.match.command.inviteaccept;

import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.match.exception.RunningInvitationClosedException;
import com.runiverse.running_service.application.match.exception.RunningInvitationNotPendingException;
import com.runiverse.running_service.application.match.exception.RunningRoomFullException;
import com.runiverse.running_service.application.match.port.in.AcceptInvitationUsecase;
import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.match.common.MatchPushRequester;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AcceptInvitationHandler implements AcceptInvitationUsecase {

    private final LockMatchRoomPort lockMatchRoomPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final InviteStartPolicy inviteStartPolicy;
    private final ExistsActiveApplicationPort existsActiveApplicationPort;
    private final LoadUserAvgPacePort loadUserAvgPacePort;
    private final CreateMatchApplicationPort createMatchApplicationPort;
    private final LoadMatchPlayersPort loadMatchPlayersPort;
    private final RoomInfoAssembler roomInfoAssembler;
    private final MatchPushRequester matchPushRequester;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AcceptInvitationResult handle(AcceptInvitationCommand command) {
        UserId inviteeId = new UserId(command.userId());
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        // 1. 방을 잠그고 다시 본다 — 먼저 수락한 순서로 자리가 차는 것을 이 잠금이 정한다.
        //    방이 없거나 이 방에서 나에게 온 초대가 없으면 둘 다 404다
        RunningRoom room = lockMatchRoomPort.lockById(roomId)
                .filter(locked -> locked.invitationStatusOf(inviteeId).isPresent())
                .orElseThrow(InvitationNotFoundException::new);
        RoomInvitationStatus status = room.invitationStatusOf(inviteeId).orElseThrow();
        // 2~4. 만료·마감 → 응답함 → 자리 없음 순서로 본다.
        //      만료는 응답이 아니라 방이 닫힌 것이라 NOT_PENDING보다 앞이다
        if (status == RoomInvitationStatus.EXPIRED
                || !inviteStartPolicy.isRecruiting(room, LocalDateTime.now())) {
            throw new RunningInvitationClosedException();
        }
        if (status != RoomInvitationStatus.PENDING) {
            throw new RunningInvitationNotPendingException();
        }
        // 자리가 없으면 초대는 PENDING 그대로다 — 누가 나가면 다시 수락할 수 있다
        if (room.getPlayerCount().isFull()) {
            throw new RunningRoomFullException();
        }
        // 5. 활성 신청은 매칭과 합쳐 하나다 — 초대받은 사이 매칭을 시작했으면 막힌다.
        //    쿨다운은 보지 않는다 — 매칭 신청만 막는다
        if (existsActiveApplicationPort.existsActive(inviteeId)) {
            throw new AlreadyRunningException();
        }
        // 6. 페이스는 온보딩 값을 쓴다(매칭과 같다)
        Pace pace = loadUserAvgPacePort.loadAvgPace(inviteeId)
                .orElseThrow(OnboardingNotCompletedException::new);
        // 7. 신청을 먼저 만들어 ID를 확보한다 — 방의 세션이 이 ID로 참조한다.
        //    시각·거리는 방을 따른다 — 초대 방은 만들 때 둘 다 정해진다
        RunningPlayer player = createMatchApplicationPort.create(RunningPlayer.request(
                command.userId(), pace.secondsPerKm(),
                room.getTargetDistance().map(Distance::meters).orElseThrow(), room.getStartAt()));
        // 8. 자리를 잡고 받은 행을 모두 수락으로 바꾼다 — 누구의 초대로 들어오든 결과가 같다.
        //    평균은 합류 전에 기존 참가자를 읽어 합류자를 더한다(합류자 세션은 아직 저장 전이다)
        List<Pace> paces = pacesAfterJoin(roomId, pace);
        room.acceptInvitation(inviteeId, player.getRunningPlayerId().orElseThrow());
        room.recalculateAvgPace(paces);
        updateMatchRoomPort.update(room);
        // 9. 기존 참가자에게 인원·초대 현황 변동을 알린다 — 수락한 사람은 응답을 받고 스트림을 열어 스냅샷을 받는다
        RoomInfo roomInfo = roomInfoAssembler.assemble(room);
        eventPublisher.publishEvent(new MatchRoomChangedEvent(MatchStreamEvent.updated(roomInfo)));
        matchPushRequester.runningInvitationAccepted(room, inviteeId, roomInfo);
        return new AcceptInvitationResult(roomId.value());
    }

    private List<Pace> pacesAfterJoin(RunningRoomId roomId, Pace joined) {
        List<Pace> paces = new ArrayList<>(loadMatchPlayersPort.loadPlayers(roomId).stream()
                .map(player -> new Pace(player.avgPaceSecondsPerKm()))
                .toList());
        paces.add(joined);
        return paces;
    }
}
