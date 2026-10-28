package com.runiverse.running_service.application.app.command.stream;

import com.runiverse.running_service.application.app.port.in.CloseAppStreamUsecase;
import com.runiverse.running_service.application.app.port.out.AppStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseAppStreamHandler implements CloseAppStreamUsecase {

    private final AppStreamPort appStreamPort;

    @Override
    public void handle(CloseAppStreamCommand command) {
        UserId userId = new UserId(command.userId());
        // 새 연결이 이미 자리를 가져갔으면 지우지 않는다 — 그때는 false다
        boolean removed = appStreamPort.remove(userId, command.connection());
        log.info("[앱] 스트림 종료 성공 - userId={}, connectionId={}, removed={}",
                command.userId(), command.connection().id(), removed);
    }
}
