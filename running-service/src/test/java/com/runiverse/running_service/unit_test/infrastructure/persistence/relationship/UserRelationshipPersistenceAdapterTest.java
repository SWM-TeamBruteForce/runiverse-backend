package com.runiverse.running_service.unit_test.infrastructure.persistence.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.port.out.FriendKeyset;
import com.runiverse.running_service.application.relationship.port.out.FriendRow;
import com.runiverse.running_service.application.relationship.port.out.UserRelationshipPair;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.infrastructure.persistence.relationship.UserRelationshipJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.relationship.UserRelationshipPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관계 영속성 어댑터 단위 테스트")
public class UserRelationshipPersistenceAdapterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<UserRelationshipJpaEntity> relationshipQuery;

    @Mock
    private Query insertQuery;

    @Mock
    private TypedQuery<FriendRow> friendQuery;

    @Mock
    private TypedQuery<Long> friendCountQuery;

    @InjectMocks
    private UserRelationshipPersistenceAdapter adapter;

    private UUID userId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        targetId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("두 행을 잠가 읽고 행의 주인에 따라 내 행과 상대 행으로 나눈다")
    void loadPairSplitsRowsByOwner() {
        // given -> id 순서로 오므로 상대 행이 먼저 올 수도 있다
        UserRelationshipJpaEntity theirs = entity(1L, targetId, userId, RelationshipType.FRIEND_REQUEST_SENT);
        UserRelationshipJpaEntity mine = entity(2L, userId, targetId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        givenLockedRows(List.of(theirs, mine));

        // when
        UserRelationshipPair pair = adapter.loadPairForUpdate(new UserId(userId), new UserId(targetId));

        // then
        assertThat(pair.findMine()).hasValueSatisfying(relationship -> {
            assertThat(relationship.getUserRelationshipId()).hasValueSatisfying(id -> assertThat(id.value()).isEqualTo(2L));
            assertThat(relationship.getUserId().value()).isEqualTo(userId);
            assertThat(relationship.getTargetId().value()).isEqualTo(targetId);
            assertThat(relationship.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
        });
        assertThat(pair.findTheirs()).hasValueSatisfying(relationship -> {
            assertThat(relationship.getUserRelationshipId()).hasValueSatisfying(id -> assertThat(id.value()).isEqualTo(1L));
            assertThat(relationship.getUserId().value()).isEqualTo(targetId);
            assertThat(relationship.getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_SENT);
        });
        // 잠그지 않으면 그사이 취소된 요청을 수락할 수 있다
        verify(relationshipQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("한쪽 행만 있으면 없는 쪽은 비어 있다")
    void loadPairWithOneRow() {
        // given
        givenLockedRows(List.of(entity(1L, userId, targetId, RelationshipType.NONE)));

        // when
        UserRelationshipPair pair = adapter.loadPairForUpdate(new UserId(userId), new UserId(targetId));

        // then
        assertThat(pair.findMine()).isPresent();
        assertThat(pair.findTheirs()).isEmpty();
    }

    @Test
    @DisplayName("행이 하나도 없으면 두 쪽 모두 비어 있다")
    void loadPairWithNoRow() {
        // given
        givenLockedRows(List.of());

        // when
        UserRelationshipPair pair = adapter.loadPairForUpdate(new UserId(userId), new UserId(targetId));

        // then
        assertThat(pair.findMine()).isEmpty();
        assertThat(pair.findTheirs()).isEmpty();
    }

    @Test
    @DisplayName("새 관계는 행을 새로 만든다")
    void saveNewRelationshipPersists() {
        // given
        UserRelationship relationship = UserRelationship.none(userId, targetId);
        relationship.sendRequest();

        // when
        adapter.save(relationship);

        // then
        ArgumentCaptor<UserRelationshipJpaEntity> captor = ArgumentCaptor.forClass(UserRelationshipJpaEntity.class);
        verify(entityManager).persist(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getTargetId()).isEqualTo(targetId);
        assertThat(captor.getValue().getType()).isEqualTo(RelationshipType.FRIEND_REQUEST_SENT);
    }

    @Test
    @DisplayName("이미 있는 관계는 같은 행의 type만 바꾼다")
    void saveExistingRelationshipChangesType() {
        // given -> 잠금 조회로 영속 상태가 된 행이다
        UserRelationshipJpaEntity entity = entity(1L, userId, targetId, RelationshipType.FRIEND_REQUEST_RECEIVED);
        when(entityManager.find(UserRelationshipJpaEntity.class, 1L)).thenReturn(entity);
        UserRelationship relationship = UserRelationship.builder()
                .userRelationshipId(1L)
                .userId(userId)
                .targetId(targetId)
                .type(RelationshipType.FRIEND_REQUEST_RECEIVED)
                .build();
        relationship.accept();

        // when
        adapter.save(relationship);

        // then
        assertThat(entity.getType()).isEqualTo(RelationshipType.FRIEND);
    }

    @Test
    @DisplayName("행 확보는 누가 호출하든 user_id 순서로 두 행을 NONE으로 넣는다")
    void ensurePairInsertsInFixedOrder() {
        // given -> A→B와 B→A가 같은 순서로 넣어야 서로를 기다리다 교착에 빠지지 않는다
        UUID smaller = userId.compareTo(targetId) < 0 ? userId : targetId;
        UUID larger = smaller.equals(userId) ? targetId : userId;
        givenInsertQuery();

        // when -> 같은 쌍을 양쪽 방향에서 호출한다
        adapter.ensurePair(new UserId(userId), new UserId(targetId));
        adapter.ensurePair(new UserId(targetId), new UserId(userId));

        // then
        verify(insertQuery, times(2)).setParameter("firstUserId", smaller);
        verify(insertQuery, times(2)).setParameter("firstTargetId", larger);
        verify(insertQuery, times(2)).setParameter("secondUserId", larger);
        verify(insertQuery, times(2)).setParameter("secondTargetId", smaller);
        verify(insertQuery, times(2)).setParameter("type", "NONE");
        // 저장 시각은 JVM 타임존 기준이라 SQL의 now()가 아니라 값으로 넘긴다
        verify(insertQuery, times(2)).setParameter(eq("now"), any(LocalDateTime.class));
        verify(insertQuery, times(2)).executeUpdate();
    }

    @Test
    @DisplayName("행 확보는 이미 있는 행을 건드리지 않는 ON CONFLICT DO NOTHING이다")
    void ensurePairIgnoresExistingRows() {
        // given
        givenInsertQuery();

        // when
        adapter.ensurePair(new UserId(userId), new UserId(targetId));

        // then -> 기존 행을 덮으면 진행 중인 요청·친구 관계가 NONE으로 지워진다
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains("ON CONFLICT (user_id, target_id) DO NOTHING");
    }

    @Test
    @DisplayName("친구 목록은 검색어·커서가 없으면 묶음 조건과 정렬만으로 읽는다")
    void loadGroupWithoutFilterOrCursor() {
        // given
        UUID viewerId = UuidCreator.getTimeOrderedEpoch();
        List<FriendRow> rows = List.of(new FriendRow(targetId, "철수", null, RelationshipType.FRIEND,
                LocalDateTime.of(2026, 10, 25, 12, 0), null));
        givenFriendQuery(rows);

        // when
        List<FriendRow> result = adapter.loadGroup(
                new UserId(userId), new UserId(viewerId), RelationshipType.FRIEND, null, null, 21);

        // then -> 쓰지 않는 조건의 파라미터를 넣으면 Hibernate가 없는 이름이라고 실패한다
        assertThat(result).isEqualTo(rows);
        String jpql = capturedFriendJpql();
        assertThat(jpql).doesNotContain("LIKE").doesNotContain(":afterUpdatedAt");
        assertThat(jpql).contains("ORDER BY r.updatedAt DESC, r.targetId DESC");
        verify(friendQuery).setParameter("ownerId", userId);
        verify(friendQuery).setParameter("viewerId", viewerId);
        verify(friendQuery).setParameter("type", RelationshipType.FRIEND);
        verify(friendQuery).setMaxResults(21);
        verify(friendQuery, never()).setParameter(eq("nicknamePattern"), any());
        verify(friendQuery, never()).setParameter(eq("afterUpdatedAt"), any());
    }

    @Test
    @DisplayName("친구 목록 커서가 있으면 그 위치 다음부터 읽는 조건을 붙인다")
    void loadGroupWithCursorAddsKeysetCondition() {
        // given
        UUID lastUserId = UuidCreator.getTimeOrderedEpoch();
        LocalDateTime lastUpdatedAt = LocalDateTime.of(2026, 10, 25, 12, 0, 0, 123_456_000);
        givenFriendQuery(List.of());

        // when
        adapter.loadGroup(new UserId(userId), new UserId(userId), RelationshipType.FRIEND_REQUEST_SENT,
                null, new FriendKeyset(lastUpdatedAt, lastUserId), 5);

        // then -> 같은 시각이면 userId로 가른다
        assertThat(capturedFriendJpql())
                .contains("r.updatedAt < :afterUpdatedAt")
                .contains("r.updatedAt = :afterUpdatedAt AND r.targetId < :afterUserId");
        verify(friendQuery).setParameter("afterUpdatedAt", lastUpdatedAt);
        verify(friendQuery).setParameter("afterUserId", lastUserId);
    }

    @Test
    @DisplayName("친구 목록 검색어는 부분 일치이고 %·_·\\는 글자 그대로 찾는다")
    void loadGroupWithNicknameEscapesWildcards() {
        // given
        givenFriendQuery(List.of());

        // when
        adapter.loadGroup(new UserId(userId), new UserId(userId), RelationshipType.FRIEND,
                "a%b_c\\d", null, 21);

        // then -> 이스케이프하지 않으면 "%"만 넣어도 모든 친구가 걸린다
        assertThat(capturedFriendJpql()).contains("o.nickname LIKE :nicknamePattern ESCAPE '\\'");
        verify(friendQuery).setParameter("nicknamePattern", "%a\\%b\\_c\\\\d%");
    }

    private void givenFriendQuery(List<FriendRow> rows) {
        when(entityManager.createQuery(anyString(), eq(FriendRow.class))).thenReturn(friendQuery);
        when(friendQuery.setParameter(anyString(), any())).thenReturn(friendQuery);
        when(friendQuery.setMaxResults(anyInt())).thenReturn(friendQuery);
        when(friendQuery.getResultList()).thenReturn(rows);
    }

    @Test
    @DisplayName("내 행이 FRIEND면 친구다")
    void isFriendWhenMyRowIsFriend() {
        // given
        givenFriendCount(1L);

        // when & then -> 친구는 대칭이라 내 행(user_id = 나)만 FRIEND로 거른다
        assertThat(adapter.isFriend(new UserId(userId), new UserId(targetId))).isTrue();
        verify(friendCountQuery).setParameter("userId", userId);
        verify(friendCountQuery).setParameter("targetId", targetId);
        verify(friendCountQuery).setParameter("type", RelationshipType.FRIEND);
        // 초대는 관계를 바꾸지 않아 잠그지 않는다
        verify(friendCountQuery, never()).setLockMode(any());
    }

    @Test
    @DisplayName("내 행이 FRIEND가 아니면 친구가 아니다")
    void isNotFriendWithoutFriendRow() {
        // given -> 요청 중·없음·차단은 모두 FRIEND가 아니다
        givenFriendCount(0L);

        // when & then
        assertThat(adapter.isFriend(new UserId(userId), new UserId(targetId))).isFalse();
    }

    private void givenFriendCount(long count) {
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(friendCountQuery);
        when(friendCountQuery.setParameter(anyString(), any())).thenReturn(friendCountQuery);
        when(friendCountQuery.getSingleResult()).thenReturn(count);
    }

    private String capturedFriendJpql() {
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(FriendRow.class));
        return jpql.getValue();
    }

    private void givenInsertQuery() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(insertQuery);
        when(insertQuery.setParameter(anyString(), any())).thenReturn(insertQuery);
    }

    private void givenLockedRows(List<UserRelationshipJpaEntity> rows) {
        when(entityManager.createQuery(anyString(), eq(UserRelationshipJpaEntity.class)))
                .thenReturn(relationshipQuery);
        when(relationshipQuery.setParameter("userId", userId)).thenReturn(relationshipQuery);
        when(relationshipQuery.setParameter("targetId", targetId)).thenReturn(relationshipQuery);
        when(relationshipQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(relationshipQuery);
        when(relationshipQuery.getResultList()).thenReturn(rows);
    }

    // 식별자는 DB가 채우므로 저장된 행을 흉내 내려면 직접 넣는다
    private UserRelationshipJpaEntity entity(Long id, UUID owner, UUID target, RelationshipType type) {
        UserRelationshipJpaEntity entity = UserRelationshipJpaEntity.create(owner, target, type);
        ReflectionTestUtils.setField(entity, "userRelationshipId", id);
        return entity;
    }
}
