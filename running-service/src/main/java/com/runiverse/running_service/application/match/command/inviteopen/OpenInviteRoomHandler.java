package com.runiverse.running_service.application.match.command.inviteopen;

import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.common.port.out.ScheduleJobPort;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InviteStartAtOutOfRangeException;
import com.runiverse.running_service.application.match.port.in.OpenInviteRoomUsecase;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.CreateMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class OpenInviteRoomHandler implements OpenInviteRoomUsecase {

    private final InviteStartPolicy inviteStartPolicy;
    private final ExistsActiveApplicationPort existsActiveApplicationPort;
    private final LoadUserAvgPacePort loadUserAvgPacePort;
    private final CreateMatchApplicationPort createMatchApplicationPort;
    private final CreateMatchRoomPort createMatchRoomPort;
    private final ScheduleJobPort scheduleJobPort;
    private final MatchProperties matchProperties;

    @Override
    public OpenInviteRoomResult handle(OpenInviteRoomCommand command) {
        LocalDateTime startAt = command.scheduledStartAt();
        // 0. 분 단위·거리 범위 — 범위가 운영값이라 Request DTO가 못 보고 여기서 본다.
        //    시각 범위보다 먼저 봐야 초가 붙은 값이 409가 아니라 400으로 나간다
        inviteStartPolicy.validate(startAt, command.targetDistanceMeters());
        // 1. 지금 고를 수 있는 시각인가 — 지금 시각에 따라 갈려 요청 검증이 아니다. DB를 안 타 앞쪽에 둔다
        if (!inviteStartPolicy.isSelectable(startAt, LocalDateTime.now())) {
            throw new InviteStartAtOutOfRangeException();
        }
        UserId userId = new UserId(command.userId());
        // 2. 활성 신청은 매칭과 합쳐 하나다 — 매칭 대기·러닝 중이면 초대 방도 못 연다.
        //    쿨다운은 보지 않는다 — 매칭 신청만 막는다
        if (existsActiveApplicationPort.existsActive(userId)) {
            throw new AlreadyRunningException();
        }
        // 3. 페이스는 입력받지 않고 온보딩 값을 쓴다(매칭과 같다)
        Pace pace = loadUserAvgPacePort.loadAvgPace(userId)
                .orElseThrow(OnboardingNotCompletedException::new);
        // 4. 신청을 먼저 만들어 ID를 확보한다 — 방의 세션이 이 ID로 참조한다
        RunningPlayer player = createMatchApplicationPort.create(RunningPlayer.request(
                command.userId(), pace.secondsPerKm(), command.targetDistanceMeters(), startAt));
        RunningRoom room = createMatchRoomPort.create(RunningRoom.openInvite(
                userId, player.getRunningPlayerId().orElseThrow(), pace.secondsPerKm(),
                command.targetDistanceMeters(), startAt));
        // 5. 예약은 매칭 방과 같은 다섯 가지다 — 마감만 초대 오프셋이고 나머지는 매칭 설정을 쓴다.
        //    방과 같은 트랜잭션이라 방이 롤백되면 예약도 사라진다
        Long roomId = room.getRunningRoomId().orElseThrow().value();
        scheduleJobPort.schedule(ScheduledJobType.MATCH_CLOSE, roomId, inviteStartPolicy.closeAt(startAt));
        scheduleJobPort.schedule(
                ScheduledJobType.RUNNING_REMINDER, roomId, startAt.minus(matchProperties.reminderOffset()));
        scheduleJobPort.schedule(
                ScheduledJobType.RUNNING_READY, roomId, startAt.minus(matchProperties.readyOffset()));
        scheduleJobPort.schedule(ScheduledJobType.RUNNING_START, roomId, startAt);
        scheduleJobPort.schedule(
                ScheduledJobType.RUNNING_FORCE_FINISH, roomId, startAt.plus(matchProperties.forceFinishOffset()));
        // 혼자인 방이라 알릴 기존 참가자가 없다 — 본인은 응답을 받고 스트림을 열어 입장 스냅샷을 받는다
        return new OpenInviteRoomResult(roomId);
    }
}
