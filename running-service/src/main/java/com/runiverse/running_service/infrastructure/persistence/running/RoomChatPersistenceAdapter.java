package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomChatPersistenceAdapter implements CreateRoomChatPort {

    private final EntityManager entityManager;

    // 방은 참조만 건다 — 유스케이스가 이미 배정을 확인했고, 방 행을 읽거나 잠글 이유가 없다.
    // IDENTITY라 persist가 곧 INSERT다 — ID와 created_at이 이 자리에서 채워진다
    @Override
    public SavedRoomChat create(RunningRoomChat chat) {
        RunningRoomChatJpaEntity entity = RunningRoomChatJpaEntity.create(
                entityManager.getReference(RunningRoomJpaEntity.class, chat.getRunningRoomId().value()),
                chat.getUserId().value(),
                chat.getContent().value());
        entityManager.persist(entity);
        return new SavedRoomChat(entity.getRunningRoomChatId(), entity.getCreatedAt());
    }
}
