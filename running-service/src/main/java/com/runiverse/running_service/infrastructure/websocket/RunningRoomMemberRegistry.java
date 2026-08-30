package com.runiverse.running_service.infrastructure.websocket;

import com.runiverse.running_service.application.running.port.out.LoadRunningRoomMembersPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// 이 인스턴스가 어느 방의 참가자를 들고 있는지만 담는다.
// 구독 제어와 명부 조회가 같은 빈을 거치면 서로를 요구해 순환이 된다 — 데이터만 여기에 둔다
@Component
public class RunningRoomMemberRegistry implements LoadRunningRoomMembersPort {

    private final Map<Long, Set<UserId>> usersByRoom = new ConcurrentHashMap<>();
    private final Map<UserId, Long> roomByUser = new ConcurrentHashMap<>();

    @Override
    public Set<UserId> usersIn(Long runningRoomId) {
        // 원본을 그대로 주면 호출자가 명부를 바꿀 수 있다 — 복사본을 준다
        return Set.copyOf(usersByRoom.getOrDefault(runningRoomId, Set.of()));
    }

    public Optional<Long> roomOf(UserId userId) {
        return Optional.ofNullable(roomByUser.get(userId));
    }

    // 그 방의 첫 참가자를 받았으면 방 번호 — 그때만 구독하면 된다.
    // 판정을 compute 안에서 해야 다른 스레드가 끼어들어도 한 번만 나온다
    public Optional<Long> join(UserId userId, Long runningRoomId) {
        roomByUser.put(userId, runningRoomId);
        AtomicBoolean first = new AtomicBoolean();
        usersByRoom.computeIfAbsent(runningRoomId, key -> {
            first.set(true);
            return ConcurrentHashMap.newKeySet();
        }).add(userId);
        return first.get() ? Optional.of(runningRoomId) : Optional.empty();
    }

    // 마지막 참가자가 빠졌으면 방 번호 — 그때만 구독을 끊는다.
    // 빈 Set도 지운다: 안 지우면 방 수만큼 샌다
    public Optional<Long> leave(UserId userId) {
        Long runningRoomId = roomByUser.remove(userId);
        if (runningRoomId == null) {
            return Optional.empty();
        }
        AtomicBoolean emptied = new AtomicBoolean();
        usersByRoom.computeIfPresent(runningRoomId, (key, users) -> {
            users.remove(userId);
            if (!users.isEmpty()) {
                return users;
            }
            emptied.set(true);
            return null;
        });
        return emptied.get() ? Optional.of(runningRoomId) : Optional.empty();
    }
}
