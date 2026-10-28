package com.runiverse.running_service.unit_test.infrastructure.persistence.notification;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.port.out.UserNotificationPage;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.UserNotification;
import com.runiverse.running_service.domain.notification.vo.FriendAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.infrastructure.persistence.notification.UserNotificationJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.notification.UserNotificationPersistenceAdapter;
import com.runiverse.running_service.support.LogCapture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// jsonb 매핑·CHECK·FK·JPQL 문법은 실제 DB에서만 확인된다 — 여기서는 저장하는 키와 값, 읽은 행을 다루는 규칙만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("알림 영속성 어댑터 단위 테스트")
class UserNotificationPersistenceAdapterTest {

    private static final UUID RECIPIENT_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID ACTOR_ID = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 9, 28, 0, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 28, 12, 0);

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserNotificationPersistenceAdapter adapter;

    @Nested
    @DisplayName("저장")
    class Save {

        private UserNotificationJpaEntity persisted() {
            ArgumentCaptor<UserNotificationJpaEntity> captor = ArgumentCaptor.forClass(UserNotificationJpaEntity.class);
            verify(entityManager).persist(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("저장하면 DB가 만든 ID를 돌려준다 — 커밋 뒤 스트림으로 밀어 줄 때 쓴다")
        void returnsGeneratedId() {
            // given -> IDENTITY라 persist가 곧바로 INSERT해 ID를 채운다
            doAnswer(invocation -> {
                ReflectionTestUtils.setField((Object) invocation.getArgument(0), "userNotificationId", 912L);
                return null;
            }).when(entityManager).persist(any(UserNotificationJpaEntity.class));

            // when
            Long id = adapter.save(UserNotification.of(new UserId(RECIPIENT_ID),
                    new FriendRequestedPayload(new UserId(ACTOR_ID))));

            // then
            assertThat(id).isEqualTo(912L);
        }

        @Test
        @DisplayName("친구 요청 알림은 request_user_id 키에 요청한 사람을 문자열로 담아 안 읽음으로 쌓는다")
        void savesFriendRequested() {
            // when
            adapter.save(UserNotification.of(new UserId(RECIPIENT_ID), new FriendRequestedPayload(new UserId(ACTOR_ID))));

            // then -> 키 이름은 저장된 약속이다
            UserNotificationJpaEntity entity = persisted();
            assertThat(entity.getUserId()).isEqualTo(RECIPIENT_ID);
            assertThat(entity.getType()).isEqualTo(UserNotificationType.FRIEND_REQUESTED);
            assertThat(entity.getPayload()).isEqualTo(Map.of("request_user_id", ACTOR_ID.toString()));
            assertThat(entity.isRead()).isFalse();
        }

        @Test
        @DisplayName("친구 수락 알림은 accept_user_id 키에 수락한 사람을 담는다")
        void savesFriendAccepted() {
            // when
            adapter.save(UserNotification.of(new UserId(RECIPIENT_ID), new FriendAcceptedPayload(new UserId(ACTOR_ID))));

            // then
            UserNotificationJpaEntity entity = persisted();
            assertThat(entity.getType()).isEqualTo(UserNotificationType.FRIEND_ACCEPTED);
            assertThat(entity.getPayload()).isEqualTo(Map.of("accept_user_id", ACTOR_ID.toString()));
        }

        @Test
        @DisplayName("초대 알림은 request_user_id에 초대한 사람, running_room_id에 방 ID를 숫자로 담는다")
        void savesRunningInvited() {
            // when
            adapter.save(UserNotification.of(new UserId(RECIPIENT_ID),
                    new RunningInvitedPayload(new UserId(ACTOR_ID), new RunningRoomId(130L))));

            // then -> 방 ID는 running_room_invitations의 bigint와 맞춰 숫자다
            UserNotificationJpaEntity entity = persisted();
            assertThat(entity.getType()).isEqualTo(UserNotificationType.RUNNING_INVITED);
            assertThat(entity.getPayload()).isEqualTo(Map.of(
                    "request_user_id", ACTOR_ID.toString(),
                    "running_room_id", 130L));
        }
    }

    @Nested
    @DisplayName("목록 읽기")
    class LoadPage {

        @Mock
        private TypedQuery<UserNotificationJpaEntity> pageQuery;

        private LogCapture log;

        @BeforeEach
        void setUp() {
            log = LogCapture.of(UserNotificationPersistenceAdapter.class);
        }

        @AfterEach
        void tearDown() {
            log.stop();
        }

        private void givenFetched(List<UserNotificationJpaEntity> fetched) {
            when(entityManager.createQuery(anyString(), eq(UserNotificationJpaEntity.class))).thenReturn(pageQuery);
            when(pageQuery.setParameter(anyString(), any())).thenReturn(pageQuery);
            when(pageQuery.setMaxResults(anyInt())).thenReturn(pageQuery);
            when(pageQuery.getResultList()).thenReturn(fetched);
        }

        private String capturedJpql() {
            ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(jpql.capture(), eq(UserNotificationJpaEntity.class));
            return jpql.getValue();
        }

        // 저장된 행은 ID가 생성돼 있어야 해 생성 결과 대신 값을 정해 둔 가짜를 쓴다
        private UserNotificationJpaEntity row(long id, UserNotificationType type, Map<String, Object> payload) {
            UserNotificationJpaEntity entity = mock(UserNotificationJpaEntity.class);
            lenient().when(entity.getUserNotificationId()).thenReturn(id);
            lenient().when(entity.getType()).thenReturn(type);
            lenient().when(entity.getPayload()).thenReturn(payload);
            lenient().when(entity.isRead()).thenReturn(false);
            lenient().when(entity.getCreatedAt()).thenReturn(CREATED_AT);
            return entity;
        }

        private UserNotificationJpaEntity requested(long id) {
            return row(id, UserNotificationType.FRIEND_REQUESTED, Map.of("request_user_id", ACTOR_ID.toString()));
        }

        @Test
        @DisplayName("첫 페이지는 보관 기간 안의 내 알림을 ID 역순으로 하나 더 읽고, 커서 조건은 붙이지 않는다")
        void firstPageQuery() {
            // given
            givenFetched(List.of());

            // when
            adapter.loadPage(new UserId(RECIPIENT_ID), null, SINCE, 20);

            // then -> 쓰지 않는 조건의 파라미터를 넣으면 Hibernate가 없는 이름이라고 실패한다
            assertThat(capturedJpql())
                    .contains("n.userId = :userId")
                    .contains("n.createdAt >= :since")
                    .contains("ORDER BY n.userNotificationId DESC")
                    .doesNotContain(":beforeId");
            verify(pageQuery).setParameter("userId", RECIPIENT_ID);
            verify(pageQuery).setParameter("since", SINCE);
            verify(pageQuery).setMaxResults(21);
            verify(pageQuery, never()).setParameter(eq("beforeId"), any());
        }

        @Test
        @DisplayName("커서가 있으면 그 ID보다 오래된 알림만 읽는다")
        void cursorQuery() {
            // given
            givenFetched(List.of());

            // when
            adapter.loadPage(new UserId(RECIPIENT_ID), 500L, SINCE, 20);

            // then
            assertThat(capturedJpql()).contains("n.userNotificationId < :beforeId");
            verify(pageQuery).setParameter("beforeId", 500L);
        }

        @Test
        @DisplayName("한 개 더 읽히면 그 앞까지만 돌려주고 마지막 행 ID를 다음 기준으로 준다")
        void nextBeforeIdFromLastRowOfPage() {
            // given -> limit 2에 3개가 읽혔다
            givenFetched(List.of(requested(30L), requested(20L), requested(10L)));

            // when
            UserNotificationPage page = adapter.loadPage(new UserId(RECIPIENT_ID), null, SINCE, 2);

            // then
            assertThat(page.rows()).extracting(UserNotificationRow::userNotificationId).containsExactly(30L, 20L);
            assertThat(page.nextBeforeId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("더 없으면 다음 기준은 null이다")
        void noNextWhenFetchedWithinLimit() {
            // given
            givenFetched(List.of(requested(30L)));

            // when
            UserNotificationPage page = adapter.loadPage(new UserId(RECIPIENT_ID), null, SINCE, 2);

            // then
            assertThat(page.nextBeforeId()).isNull();
        }

        @Test
        @DisplayName("jsonb를 저장 때의 키로 되살린다 — 방 ID는 크기에 따라 Integer로 읽혀도 받는다")
        void restoresPayloads() {
            // given
            givenFetched(List.of(
                    requested(30L),
                    row(20L, UserNotificationType.FRIEND_ACCEPTED, Map.of("accept_user_id", ACTOR_ID.toString())),
                    row(10L, UserNotificationType.RUNNING_INVITED, Map.of(
                            "request_user_id", ACTOR_ID.toString(), "running_room_id", 130))));

            // when
            UserNotificationPage page = adapter.loadPage(new UserId(RECIPIENT_ID), null, SINCE, 20);

            // then
            assertThat(page.rows()).extracting(UserNotificationRow::payload).containsExactly(
                    new FriendRequestedPayload(new UserId(ACTOR_ID)),
                    new FriendAcceptedPayload(new UserId(ACTOR_ID)),
                    new RunningInvitedPayload(new UserId(ACTOR_ID), new RunningRoomId(130L)));
            assertThat(page.rows().getFirst().createdAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("되살리지 못한 행은 그 행만 빼고 에러를 남기며, 다음 기준은 뺀 행까지 포함해 정한다")
        void skipsBrokenRowsButKeepsCursorPosition() {
            // given -> 가운데 행은 키가 없고, 마지막 행은 페이지의 끝이다
            givenFetched(List.of(
                    requested(30L),
                    row(20L, UserNotificationType.RUNNING_INVITED, Map.of("request_user_id", ACTOR_ID.toString())),
                    row(15L, UserNotificationType.FRIEND_REQUESTED, Map.of("request_user_id", "not-a-uuid")),
                    requested(10L)));

            // when
            UserNotificationPage page = adapter.loadPage(new UserId(RECIPIENT_ID), null, SINCE, 3);

            // then -> 걸러낸 뒤 개수로 판단하면 다음 페이지를 잃는다
            assertThat(page.rows()).extracting(UserNotificationRow::userNotificationId).containsExactly(30L);
            assertThat(page.nextBeforeId()).isEqualTo(15L);
            assertThat(log.messages(Level.ERROR)).containsExactly(
                    "[알림] 목록 항목 건너뜀: payload 복원 실패 - notificationId=20, type=RUNNING_INVITED",
                    "[알림] 목록 항목 건너뜀: payload 복원 실패 - notificationId=15, type=FRIEND_REQUESTED");
        }
    }

    @Nested
    @DisplayName("한 건 읽기")
    class LoadOne {

        private LogCapture log;

        @BeforeEach
        void setUp() {
            log = LogCapture.of(UserNotificationPersistenceAdapter.class);
        }

        @AfterEach
        void tearDown() {
            log.stop();
        }

        private UserNotificationJpaEntity stored(UUID owner, Map<String, Object> payload) {
            UserNotificationJpaEntity entity = mock(UserNotificationJpaEntity.class);
            lenient().when(entity.getUserNotificationId()).thenReturn(912L);
            lenient().when(entity.getUserId()).thenReturn(owner);
            lenient().when(entity.getType()).thenReturn(UserNotificationType.FRIEND_REQUESTED);
            lenient().when(entity.getPayload()).thenReturn(payload);
            lenient().when(entity.getCreatedAt()).thenReturn(CREATED_AT);
            return entity;
        }

        @Test
        @DisplayName("받는 사람의 알림이면 되살려 돌려준다")
        void loadsOwnNotification() {
            // given
            UserNotificationJpaEntity entity = stored(RECIPIENT_ID, Map.of("request_user_id", ACTOR_ID.toString()));
            when(entityManager.find(UserNotificationJpaEntity.class, 912L)).thenReturn(entity);

            // when & then
            assertThat(adapter.loadOne(new UserId(RECIPIENT_ID), 912L)).hasValueSatisfying(row -> {
                assertThat(row.userNotificationId()).isEqualTo(912L);
                assertThat(row.payload()).isEqualTo(new FriendRequestedPayload(new UserId(ACTOR_ID)));
            });
        }

        @Test
        @DisplayName("없는 알림이면 비어 있다")
        void emptyWhenMissing() {
            // given
            when(entityManager.find(UserNotificationJpaEntity.class, 912L)).thenReturn(null);

            // when & then
            assertThat(adapter.loadOne(new UserId(RECIPIENT_ID), 912L)).isEmpty();
        }

        @Test
        @DisplayName("받는 사람이 다르면 없는 것과 같다")
        void emptyWhenOwnedByOther() {
            // given -> 남의 알림을 밀어 주지 않는다
            UserNotificationJpaEntity entity = stored(ACTOR_ID, Map.of("request_user_id", RECIPIENT_ID.toString()));
            when(entityManager.find(UserNotificationJpaEntity.class, 912L)).thenReturn(entity);

            // when & then
            assertThat(adapter.loadOne(new UserId(RECIPIENT_ID), 912L)).isEmpty();
        }

        @Test
        @DisplayName("되살리지 못한 행이면 비우고 목록과 같은 에러를 남긴다")
        void emptyWhenBroken() {
            // given
            UserNotificationJpaEntity entity = stored(RECIPIENT_ID, Map.of("unknown", 1));
            when(entityManager.find(UserNotificationJpaEntity.class, 912L)).thenReturn(entity);

            // when & then
            assertThat(adapter.loadOne(new UserId(RECIPIENT_ID), 912L)).isEmpty();
            assertThat(log.messages(Level.ERROR)).containsExactly(
                    "[알림] 목록 항목 건너뜀: payload 복원 실패 - notificationId=912, type=FRIEND_REQUESTED");
        }
    }

    @Nested
    @DisplayName("안 읽은 수")
    class CountUnread {

        @Mock
        private TypedQuery<Long> countQuery;

        @Test
        @DisplayName("보관 기간 안의 내 안 읽은 알림을 센다")
        void countsUnreadWithinRetention() {
            // given
            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(3L);

            // when
            long count = adapter.countUnread(new UserId(RECIPIENT_ID), SINCE);

            // then
            assertThat(count).isEqualTo(3L);
            ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(jpql.capture(), eq(Long.class));
            assertThat(jpql.getValue()).contains("n.read = false").contains("n.createdAt >= :since");
            verify(countQuery).setParameter("userId", RECIPIENT_ID);
            verify(countQuery).setParameter("since", SINCE);
        }
    }

    @Nested
    @DisplayName("모두 읽음")
    class MarkAllRead {

        @Mock
        private Query updateQuery;

        @Test
        @DisplayName("내 안 읽은 알림만 읽음으로 바꾸며 갱신 시각을 직접 세팅하고 바꾼 수를 돌려준다")
        void marksUnreadAndSetsUpdatedAt() {
            // given
            LocalDateTime now = LocalDateTime.of(2026, 10, 28, 20, 0);
            when(entityManager.createQuery(anyString())).thenReturn(updateQuery);
            when(updateQuery.setParameter(anyString(), any())).thenReturn(updateQuery);
            when(updateQuery.executeUpdate()).thenReturn(3);

            // when
            int readCount = adapter.markAllRead(new UserId(RECIPIENT_ID), now);

            // then -> 벌크 갱신은 @UpdateTimestamp를 거치지 않는다
            assertThat(readCount).isEqualTo(3);
            ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(jpql.capture());
            assertThat(jpql.getValue())
                    .contains("SET n.read = true, n.updatedAt = :now")
                    .contains("n.userId = :userId")
                    .contains("n.read = false")
                    .doesNotContain("createdAt");
            verify(updateQuery).setParameter("userId", RECIPIENT_ID);
            verify(updateQuery).setParameter("now", now);
        }
    }
}
