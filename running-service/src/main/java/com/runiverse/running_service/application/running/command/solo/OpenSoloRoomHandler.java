package com.runiverse.running_service.application.running.command.solo;

import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.running.port.in.OpenSoloRoomUsecase;
import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class OpenSoloRoomHandler implements OpenSoloRoomUsecase {

    private final ExistsActiveRunningPlayerPort existsActiveRunningPlayerPort;
    private final LoadUserAvgPacePort loadUserAvgPacePort;
    private final CreateRunningPlayerPort createRunningPlayerPort;
    private final CreateRunningRoomPort createRunningRoomPort;

    @Override
    public OpenSoloRoomResult handle(OpenSoloRoomCommand command) {
        UserId userId = new UserId(command.userId());
        // 1. "한 플레이어 = 최대 한 방"은 DB가 강제하지 않는다 — 앱이 막는다
        if (existsActiveRunningPlayerPort.existsActive(userId)) {
            throw new AlreadyRunningException();
        }
        // 2. 페이스는 입력받지 않고 온보딩 값을 쓴다.
        //    온보딩 완료 = user_onboardings row 존재라, 비어 있으면 곧 온보딩 미완료다
        Pace avgPace = loadUserAvgPacePort.loadAvgPace(userId)
                .orElseThrow(OnboardingNotCompletedException::new);
        // 3. 신청을 먼저 만들어 ID를 확보한다 — 방의 세션이 이 ID로 참조한다
        LocalDateTime startAt = LocalDateTime.now();
        RunningPlayer player = createRunningPlayerPort.create(
                RunningPlayer.requestSolo(command.userId(), avgPace.secondsPerKm(), startAt));
        RunningPlayerId playerId = player.getRunningPlayerId().orElseThrow();
        // 4. 방은 모집 없이 MATCHED로 태어나고 세션 링크도 openSolo가 같이 만든다.
        //    STARTED·RUNNING 전이는 이 API가 만들지 않는다 — 채널 입장 이후 구간이다.
        //    방의 target_distance는 nullable이라 목표 없는 솔로는 null이 정본이다
        //    (player 쪽은 NOT NULL이라 Distance.unlimited()가 들어간다)
        RunningRoom room = createRunningRoomPort.create(
                RunningRoom.openSolo(userId, playerId, avgPace.secondsPerKm(), null, startAt));
        return new OpenSoloRoomResult(
                room.getRunningRoomId().orElseThrow().value(), startAt);
    }
}
