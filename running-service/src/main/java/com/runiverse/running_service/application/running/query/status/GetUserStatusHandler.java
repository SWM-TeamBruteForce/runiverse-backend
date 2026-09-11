package com.runiverse.running_service.application.running.query.status;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.application.running.port.in.GetUserStatusUsecase;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserStatusHandler implements GetUserStatusUsecase {

    private final LoadUserStatusPort loadUserStatusPort;
    private final MatchCooldownPort matchCooldownPort;
    private final MatchProperties matchProperties;

    @Override
    public GetUserStatusResult handle(GetUserStatusQuery query) {
        UserId userId = new UserId(query.userId());
        // 쿨다운은 활동 중이 아닐 때만 화면에 쓰이지만 항상 함께 읽는다 —
        // 상태를 보고 조회를 건너뛰면 분기가 늘고, Redis 키 하나 읽기가 그 분기보다 싸다
        LocalDateTime cooldownUntil = matchCooldownPort.until(userId).orElse(null);
        return loadUserStatusPort.loadStatus(userId)
                .map(row -> toResult(row, cooldownUntil))
                // 활성 신청이 없으면 그것이 곧 IDLE이다 — 방을 찾지 못한 것이 아니라 없는 것이다
                .orElseGet(() -> GetUserStatusResult.idle(cooldownUntil));
    }

    private GetUserStatusResult toResult(UserStatusRow row, LocalDateTime cooldownUntil) {
        UserRunningStatus status = UserStatusResolver.resolve(
                row, LocalDateTime.now(), matchProperties.closeOffset());
        if (status == UserRunningStatus.IDLE) {
            // 닫힌 방이 활성 신청으로 남은 어긋난 데이터 — 방 값을 실어 보내면
            // 클라가 없는 방에 붙으러 간다
            return GetUserStatusResult.idle(cooldownUntil);
        }
        return new GetUserStatusResult(
                status,
                row.type(),
                row.runningRoomId(),
                row.scheduledStartAt(),
                row.targetDistanceMeters(),
                cooldownUntil);
    }
}
