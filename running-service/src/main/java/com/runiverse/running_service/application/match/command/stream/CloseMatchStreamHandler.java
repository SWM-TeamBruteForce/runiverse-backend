package com.runiverse.running_service.application.match.command.stream;

import com.runiverse.running_service.application.match.port.in.CloseMatchStreamUsecase;
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

    @Override
    public void handle(CloseMatchStreamCommand command) {
        // 새 연결이 이미 자리를 가져갔으면 지우지 않는다 — 그때는 false다
        boolean removed = matchStreamPort.remove(new UserId(command.userId()), command.connection());
        log.info("매칭 스트림 종료 — userId={}, connectionId={}, 레지스트리제거={}",
                command.userId(), command.connection().id(), removed);
    }
}
