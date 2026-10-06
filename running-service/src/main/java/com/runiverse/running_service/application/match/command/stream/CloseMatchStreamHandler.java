package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.application.match.port.in.CloseMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseMatchStreamHandler implements CloseMatchStreamUsecase {

    private final MatchStreamPort matchStreamPort;
    private final MatchRoomMembershipPort matchRoomMembershipPort;

    @Override
    public void handle(CloseMatchStreamCommand command) {
        UserId userId = new UserId(command.userId());
        // 새 연결이 이미 자리를 가져갔으면 지우지 않는다 — 그때는 false다
        boolean removed = matchStreamPort.remove(userId, command.connection());
        if (removed) {
            // 내 연결이 실제로 빠졌을 때만 방에서도 뺀다 — 새 연결이 가져간 자리는 건드리지 않는다.
            // 그 방의 마지막 참가자였으면 채널 구독도 끊긴다
            matchRoomMembershipPort.leave(userId);
        }
        log.info("[매칭] 스트림 종료 성공 - userId={}, connectionId={}, removed={}",
                command.userId(), command.connection().id(), removed);
    }
}
