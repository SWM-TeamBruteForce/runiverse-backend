package com.runiverse.running_service.unit_test.infrastructure.persistence.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
