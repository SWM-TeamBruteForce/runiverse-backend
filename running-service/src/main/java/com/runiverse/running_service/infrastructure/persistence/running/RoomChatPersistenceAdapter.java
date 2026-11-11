package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatsPort;
import com.runiverse.running_service.application.match.port.out.RoomChatPage;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomChatPersistenceAdapter implements CreateRoomChatPort, LoadRoomChatsPort {

    // 화면에 필요한 값만 바로 뽑는다 — 엔티티를 올리지 않는다.
    // c.room.runningRoomId는 FK 컬럼을 그대로 읽어 방 테이블과 조인하지 않는다
    private static final String SELECT_PAGE = """
            SELECT NEW com.runiverse.running_service.application.match.port.out.RoomChatRow(
                c.runningRoomChatId, c.room.runningRoomId, c.userId, c.content,
                c.editedAt, c.deletedAt, c.createdAt)
            FROM RunningRoomChatJpaEntity c
            WHERE c.room.runningRoomId = :runningRoomId
            """;
    private static final String BEFORE_CONDITION = " AND c.runningRoomChatId < :beforeId";
    // 인덱스 (running_room_id, running_room_chat_id)를 역방향으로 타 최신순·커서 조건을 인덱스가 처리한다
    private static final String ORDER_BY = " ORDER BY c.runningRoomChatId DESC";

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

    // 지운 메시지도 빼지 않는다 — 자리를 지키고, 내용을 숨기는 건 조립기의 몫이다
    @Override
    public RoomChatPage loadPage(Long runningRoomId, Long beforeId, int limit) {
        String jpql = SELECT_PAGE + (beforeId == null ? "" : BEFORE_CONDITION) + ORDER_BY;
        TypedQuery<RoomChatRow> query = entityManager.createQuery(jpql, RoomChatRow.class)
                .setParameter("runningRoomId", runningRoomId)
                // 하나 더 읽어 다음 페이지가 있는지 본다
                .setMaxResults(limit + 1);
        if (beforeId != null) {
            query.setParameter("beforeId", beforeId);
        }
        List<RoomChatRow> fetched = query.getResultList();
        boolean hasNext = fetched.size() > limit;
        List<RoomChatRow> rows = hasNext ? fetched.subList(0, limit) : fetched;
        Long nextBeforeId = hasNext ? rows.getLast().chatId() : null;
        return new RoomChatPage(rows, nextBeforeId);
    }
}
