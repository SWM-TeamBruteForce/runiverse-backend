package com.runiverse.running_service.infrastructure.websocket;

import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunningSessionRegistryAdapter implements RunningSessionPort {

    // 이 인스턴스에 붙어 있는 연결만 담는다 - 다른 서버 것은 없다
    private final Map<UserId, RunningConnection> connectionByUser = new ConcurrentHashMap<>();

    // 마지막 연결이 이긴다 - 밀려난 이전 연결을 돌려주면 호출자가 닫는다
    @Override
    public Optional<RunningConnection> register(UserId userId, RunningConnection connection) {
        RunningConnection superseded = connectionByUser.put(userId, connection);
        if (superseded == null || superseded.id().equals(connection.id())) {
            return Optional.empty();   // 같은 소켓의 재전송이면 밀어낼 것이 없다
        }
        return Optional.of(superseded);
    }

    @Override
    public boolean remove(UserId userId, RunningConnection connection) {
        // 새 연결이 이미 자리를 가져갔으면 그 매핑까지 지우면 안 된다 - 값이 같을 때만 지운다
        return connectionByUser.remove(userId, connection);
    }

    @Override
    public Optional<RunningConnection> find(UserId userId) {
        return Optional.ofNullable(connectionByUser.get(userId));
    }
}
