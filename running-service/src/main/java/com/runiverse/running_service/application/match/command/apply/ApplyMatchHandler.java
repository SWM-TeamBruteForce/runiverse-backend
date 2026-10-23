package com.runiverse.running_service.application.match.command.apply;

import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.MatchSlotPolicy;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.MatchAlreadyInProgressException;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.application.match.exception.MatchSlotClosedException;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplyMatchHandler implements ApplyMatchUsecase {

    private final MatchCooldownPort matchCooldownPort;
    private final ExistsActiveApplicationPort existsActiveApplicationPort;
    private final LoadUserAvgPacePort loadUserAvgPacePort;
    private final CreateMatchApplicationPort createMatchApplicationPort;
    private final MatchRoomAssigner matchRoomAssigner;
    private final MatchSlotPolicy matchSlotPolicy;
    private final RoomInfoAssembler roomInfoAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ApplyMatchResult handle(ApplyMatchCommand command) {
        // 0. 고를 수 있는 시각·거리인가 — 선택지가 운영값이라 Request DTO가 못 보고 여기서 본다.
        //    마감보다 먼저 봐야 03:00처럼 애초에 없는 슬롯이 409 마감이 아니라 400으로 나간다
        matchSlotPolicy.validate(command.scheduledStartAt(), command.targetDistanceMeters());
        // 1. 모집이 끝난 슬롯인가 — DB를 안 타는 검사라 앞쪽에 둔다.
        //    경계는 슬롯 조회의 selectable과 같아야 해 정책이 함께 판정한다
        if (!matchSlotPolicy.isOpen(command.scheduledStartAt(), LocalDateTime.now())) {
            throw new MatchSlotClosedException();
        }
        UserId userId = new UserId(command.userId());
        // 2. 제재 이탈로 막혀 있나 — Redis 키 하나라 DB 조회보다 앞에 둔다.
        //    근거(status=*_LEFT_PENALTY)는 DB에 남아 있고, "지금 막혀 있나"는 이 키가 답한다
        matchCooldownPort.until(userId).ifPresent(cooldownUntil -> {
            throw new MatchCooldownException(cooldownUntil);
        });
        // 3. 활성 신청은 하나다 — deleted_at만 보므로 러닝 중(RUNNING)도 여기서 막힌다.
        //    "한 플레이어 = 최대 한 방"은 DB가 강제하지 않는다. 앱이 막는다
        if (existsActiveApplicationPort.existsActive(userId)) {
            throw new MatchAlreadyInProgressException();
        }
        // 4. 페이스는 입력받지 않고 온보딩 값을 쓴다.
        //    온보딩 완료 = user_onboardings row 존재라, 비어 있으면 곧 온보딩 미완료다
        Pace pace = loadUserAvgPacePort.loadAvgPace(userId)
                .orElseThrow(OnboardingNotCompletedException::new);
        // 5. 신청을 먼저 만들어 ID를 확보한다 — 방의 세션이 이 ID로 참조한다
        RunningPlayer player = createMatchApplicationPort.create(RunningPlayer.request(
                command.userId(), pace.secondsPerKm(),
                command.targetDistanceMeters(), command.scheduledStartAt()));
        // 6. 붙을 방을 찾거나 새로 연다
        RunningRoom room = matchRoomAssigner.assign(
                userId, player.getRunningPlayerId().orElseThrow(), pace,
                command.scheduledStartAt(), command.targetDistanceMeters());
        // 7. 기존 참가자에게 인원 변동을 알린다 — 신청자 본인은 아직 스트림을 열기 전이다.
        //    조립은 트랜잭션 안에서 한다(자기 쓰기를 보고, 커밋 후 리스너가 DB를 다시 안 읽는다)
        eventPublisher.publishEvent(new MatchRoomChangedEvent(
                MatchStreamEvent.updated(roomInfoAssembler.assemble(room))));
        return new ApplyMatchResult(room.getRunningRoomId().orElseThrow().value());
    }

}
