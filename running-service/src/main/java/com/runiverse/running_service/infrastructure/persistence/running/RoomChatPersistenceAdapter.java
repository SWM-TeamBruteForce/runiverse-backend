package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.match.port.out.CreateRoomChatHistoryPort;
import com.runiverse.running_service.application.match.port.out.CreateRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatPort;
import com.runiverse.running_service.application.match.port.out.LoadRoomChatsPort;
import com.runiverse.running_service.application.match.port.out.LockedRoomChat;
import com.runiverse.running_service.application.match.port.out.RoomChatPage;
import com.runiverse.running_service.application.match.port.out.RoomChatRow;
import com.runiverse.running_service.application.match.port.out.SavedRoomChat;
import com.runiverse.running_service.application.match.port.out.UpdateRoomChatPort;
import com.runiverse.running_service.domain.running.room.RunningRoomChat;
import com.runiverse.running_service.domain.running.room.RunningRoomChatHistory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoomChatPersistenceAdapter implements CreateRoomChatPort, LoadRoomChatsPort,
        LoadRoomChatPort, UpdateRoomChatPort, CreateRoomChatHistoryPort {

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
    // 고치거나 지울 메시지 한 행만 잠근다 — c.room.runningRoomId는 FK 컬럼이라 방 테이블과 조인하지 않아 방 행은 잠기지 않는다.
    // 방 행까지 잠그면 대기방의 참가·이탈과 서로 기다린다(RunningRoomChat이 방과 다른 애그리거트인 이유)
    private static final String SELECT_FOR_UPDATE = """
            SELECT c
            FROM RunningRoomChatJpaEntity c
            WHERE c.runningRoomChatId = :chatId
              AND c.room.runningRoomId = :runningRoomId
            """;

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

    // 지운 메시지도 돌려준다 — 404 세 경우(없음·지움·남의 것)는 유스케이스가 한 곳에서 가른다
    @Override
    public Optional<LockedRoomChat> loadForUpdate(Long runningRoomId, Long chatId) {
        return entityManager.createQuery(SELECT_FOR_UPDATE, RunningRoomChatJpaEntity.class)
                .setParameter("chatId", chatId)
                .setParameter("runningRoomId", runningRoomId)
                // 연달아 온 두 수정이 같은 직전 내용을 읽으면 이력 하나가 빠진다 — 앞 트랜잭션의 커밋을 기다린다
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList()
                .stream()
                .findFirst()
                .map(entity -> new LockedRoomChat(
                        RunningRoomChat.restore(entity.getRunningRoomChatId(), runningRoomId, entity.getUserId(),
                                entity.getContent(), entity.getEditedAt(), entity.getDeletedAt()),
                        entity.getCreatedAt()));
    }

    // loadForUpdate로 잠근 행이라 영속성 컨텍스트에 있다 — find는 SELECT 없이 그 엔티티를 돌려주고, UPDATE는 커밋 때 나간다
    @Override
    public void update(RunningRoomChat chat) {
        RunningRoomChatJpaEntity entity = entityManager.find(RunningRoomChatJpaEntity.class,
                chat.getRunningRoomChatId().orElseThrow().value());
        entity.apply(chat.getContent().value(),
                chat.getEditedAt().orElse(null),
                chat.getDeletedAt().orElse(null));
    }

    // 메시지는 참조만 건다 — 이미 잠가 읽었다. IDENTITY라 persist가 곧 INSERT다(이력이 내용 UPDATE보다 먼저 나간다)
    @Override
    public void create(RunningRoomChatHistory history) {
        entityManager.persist(RunningRoomChatHistoryJpaEntity.create(
                entityManager.getReference(RunningRoomChatJpaEntity.class, history.getRunningRoomChatId().value()),
                history.getContent().value()));
    }
}
