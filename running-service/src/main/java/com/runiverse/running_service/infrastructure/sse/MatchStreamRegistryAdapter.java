package com.runiverse.running_service.infrastructure.sse;

import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MatchStreamRegistryAdapter implements MatchStreamPort {

    // 이 인스턴스에 붙어 있는 연결만 담는다 — 다른 서버 것은 없다
    private final Map<UserId, MatchStreamConnection> connectionByUser = new ConcurrentHashMap<>();

    @Override
    public Optional<MatchStreamConnection> register(UserId userId, MatchStreamConnection connection) {
        MatchStreamConnection superseded = connectionByUser.put(userId, connection);
        if (superseded == null || superseded.id().equals(connection.id())) {
            return Optional.empty();
        }
        return Optional.of(superseded);
    }

    @Override
    public boolean remove(UserId userId, MatchStreamConnection connection) {
        // 새 연결이 이미 자리를 가져갔으면 그 매핑까지 지우면 안 된다 - 값이 같을 때만 지운다
        return connectionByUser.remove(userId, connection);
    }

    // keep-alive 전용 — 포트에 두지 않는다. 유스케이스가 아니라 연결 유지라는 기술 관심사다
    Collection<MatchStreamConnection> all() {
        // 순회 중 등록·해제가 일어나도 안전하게 스냅샷을 준다
        return List.copyOf(connectionByUser.values());
    }
}
