package com.runiverse.running_service.infrastructure.persistence.notification;

import com.runiverse.running_service.application.notification.port.out.CountUnreadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.LoadUserNotificationsPort;
import com.runiverse.running_service.application.notification.port.out.MarkUserNotificationsReadPort;
import com.runiverse.running_service.application.notification.port.out.SaveUserNotificationPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationPage;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.UserNotification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationPersistenceAdapter implements SaveUserNotificationPort, LoadUserNotificationsPort,
        CountUnreadUserNotificationsPort, MarkUserNotificationsReadPort {

    // idx_user_notification_list(user_id, user_notification_id)를 거꾸로 읽는다.
    // 보관 기간(created_at)은 읽은 행에 거는 필터다 — ID와 생성 시각의 순서가 같아 경계 밖은 끝에 몰려 있다
    private static final String SELECT_PAGE = """
            SELECT n FROM UserNotificationJpaEntity n
            WHERE n.userId = :userId
              AND n.createdAt >= :since
            """;
    private static final String BEFORE_CONDITION = """
              AND n.userNotificationId < :beforeId
            """;
    private static final String ORDER_BY = """
            ORDER BY n.userNotificationId DESC
            """;
    // 부분 인덱스(user_id) WHERE is_read = false를 탄다 — 운영 SQL에만 있다
    private static final String COUNT_UNREAD = """
            SELECT COUNT(n) FROM UserNotificationJpaEntity n
            WHERE n.userId = :userId
              AND n.read = false
              AND n.createdAt >= :since
            """;

    // 벌크 갱신은 엔티티를 거치지 않아 @UpdateTimestamp가 돌지 않는다 — updated_at을 직접 세팅한다.
    // 부분 인덱스(user_id) WHERE is_read = false로 바꿀 행만 찾는다
    private static final String MARK_ALL_READ = """
            UPDATE UserNotificationJpaEntity n
            SET n.read = true, n.updatedAt = :now
            WHERE n.userId = :userId
              AND n.read = false
            """;

    private final EntityManager entityManager;

    @Override
    public Long save(UserNotification notification) {
        // 종류는 payload가 정한다 — 컬럼과 jsonb가 어긋날 수 없다
        UserNotificationJpaEntity entity = UserNotificationJpaEntity.create(
                notification.getUserId().value(),
                notification.type(),
                UserNotificationPayloadMapper.toJson(notification.getPayload()));
        // IDENTITY라 persist가 곧바로 INSERT해 ID가 채워진다
        entityManager.persist(entity);
        return entity.getUserNotificationId();
    }

    @Override
    public UserNotificationPage loadPage(UserId userId, Long beforeId, LocalDateTime since, int limit) {
        String jpql = SELECT_PAGE + (beforeId == null ? "" : BEFORE_CONDITION) + ORDER_BY;
        TypedQuery<UserNotificationJpaEntity> query = entityManager
                .createQuery(jpql, UserNotificationJpaEntity.class)
                .setParameter("userId", userId.value())
                .setParameter("since", since)
                // 하나 더 읽어 다음 페이지가 있는지 본다
                .setMaxResults(limit + 1);
        if (beforeId != null) {
            query.setParameter("beforeId", beforeId);
        }
        List<UserNotificationJpaEntity> fetched = query.getResultList();
        boolean hasNext = fetched.size() > limit;
        List<UserNotificationJpaEntity> page = hasNext ? fetched.subList(0, limit) : fetched;
        // 다음 페이지 기준은 걸러내기 전의 마지막 행이다 — 뺀 행 때문에 페이지가 짧아져도 다음 위치는 그대로다
        Long nextBeforeId = hasNext ? page.getLast().getUserNotificationId() : null;
        List<UserNotificationRow> rows = page.stream()
                .map(UserNotificationPersistenceAdapter::toRow)
                .flatMap(Optional::stream)
                .toList();
        return new UserNotificationPage(rows, nextBeforeId);
    }
    
    @Override
    public Optional<UserNotificationRow> loadOne(UserId userId, Long userNotificationId) {
        // PK로 찾는다. 받는 사람이 다르면 없는 것과 같다 — 남의 알림을 밀어 주지 않는다
        UserNotificationJpaEntity entity = entityManager.find(UserNotificationJpaEntity.class, userNotificationId);
        if (entity == null || !entity.getUserId().equals(userId.value())) {
            return Optional.empty();
        }
        // 되살리지 못한 행은 목록과 같게 로그를 남기고 비운다
        return toRow(entity);
    }

    @Override
    public long countUnread(UserId userId, LocalDateTime since) {
        return entityManager.createQuery(COUNT_UNREAD, Long.class)
                .setParameter("userId", userId.value())
                .setParameter("since", since)
                .getSingleResult();
    }

    @Override
    public int markAllRead(UserId userId, LocalDateTime now) {
        return entityManager.createQuery(MARK_ALL_READ)
                .setParameter("userId", userId.value())
                .setParameter("now", now)
                .executeUpdate();
    }

    // 되살리지 못한 행은 그 행만 뺀다 — 알림 하나 때문에 알림함 전체가 열리지 않으면 안 된다.
    // 생기면 안 되는 데이터라 원인을 볼 수 있게 스택과 함께 남긴다
    private static Optional<UserNotificationRow> toRow(UserNotificationJpaEntity entity) {
        try {
            return Optional.of(new UserNotificationRow(
                    entity.getUserNotificationId(),
                    UserNotificationPayloadMapper.fromJson(entity.getType(), entity.getPayload()),
                    entity.isRead(),
                    entity.getCreatedAt()));
        } catch (RuntimeException e) {
            log.error("[알림] 목록 항목 건너뜀: payload 복원 실패 - notificationId={}, type={}",
                    entity.getUserNotificationId(), entity.getType(), e);
            return Optional.empty();
        }
    }
}
