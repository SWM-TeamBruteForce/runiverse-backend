package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.application.match.port.in.CloseSupersededMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseSupersededMatchStreamHandler implements CloseSupersededMatchStreamUsecase {

    private final MatchStreamPort matchStreamPort;
    private final MatchRoomMembershipPort matchRoomMembershipPort;

    @Override
    public void handle(CloseSupersededMatchStreamCommand command) {
        UserId userId = new UserId(command.userId());
        // 자기가 보낸 알림도 되받는다 — 이긴 연결은 남긴다
        Optional<MatchStreamConnection> superseded = matchStreamPort.find(userId)
                .filter(connection -> !connection.id().equals(command.winnerConnectionId()));
        if (superseded.isEmpty()) {
            return;
        }
        MatchStreamConnection connection = superseded.get();
        // 닫기 전에 표와 명부에서 먼저 뺀다 — 종료 처리 순서에 기대지 않고, 늦게 도는 종료 처리는 가드에 걸려 아무것도 하지 않는다.
        // 그 사이 이 인스턴스에 더 새 연결이 붙었으면 remove가 false다 — 그 연결의 register가 이미 닫았다
        if (!matchStreamPort.remove(userId, connection)) {
            return;
        }
        matchRoomMembershipPort.leave(userId);
        connection.closeSuperseded();
        log.info("[매칭] 다른 인스턴스 연결로 대체 성공 - userId={}, connectionId={}, winnerConnectionId={}",
                command.userId(), connection.id(), command.winnerConnectionId());
    }
}
