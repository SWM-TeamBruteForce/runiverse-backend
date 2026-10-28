package com.runiverse.running_service.application.app.command.stream;

import com.runiverse.running_service.application.app.port.in.OpenAppStreamUsecase;
import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.application.app.port.out.AppStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAppStreamHandler implements OpenAppStreamUsecase {

    private final AppStreamPort appStreamPort;

    @Override
    public void handle(OpenAppStreamCommand command) {
        UserId userId = new UserId(command.userId());
        AppStreamConnection connection = command.connection();
        // 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다 — 마지막 연결만 남긴다
        appStreamPort.register(userId, connection)
                .ifPresent(AppStreamConnection::closeSuperseded);
        log.info("[앱] 스트림 연결 성공 - userId={}, connectionId={}", command.userId(), connection.id());
    }
}
