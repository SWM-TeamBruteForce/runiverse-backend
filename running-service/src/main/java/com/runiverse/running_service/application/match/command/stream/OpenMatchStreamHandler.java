package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.application.match.port.in.OpenMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenMatchStreamHandler implements OpenMatchStreamUsecase {

    private final MatchStreamPort matchStreamPort;

    @Override
    public void handle(OpenMatchStreamCommand command) {
        MatchStreamConnection connection = command.connection();
        // 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다 — 마지막 연결만 남긴다
        matchStreamPort.register(new UserId(command.userId()), connection)
                .ifPresent(MatchStreamConnection::closeSuperseded);
        log.info("매칭 스트림 연결 — userId={}, connectionId={}", command.userId(), connection.id());
        // 다음 단계: 활성 신청 확인 → RoomInfo 스냅샷 발신
    }
}
