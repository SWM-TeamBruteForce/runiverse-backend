package com.runiverse.running_service.unit_test.infrastructure.persistence.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.port.out.RecommendationCandidate;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.infrastructure.persistence.relationship.FriendRecommendationPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 친구 영속성 어댑터 단위 테스트")
public class FriendRecommendationPersistenceAdapterTest {

    private static final List<RelationshipType> UNAVAILABLE_TYPES = List.of(
            RelationshipType.FRIEND, RelationshipType.FRIEND_REQUEST_SENT, RelationshipType.FRIEND_REQUEST_RECEIVED);

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Long> roomQuery;

    @Mock
    private TypedQuery<RecommendationCandidate> candidateQuery;

    @InjectMocks
    private FriendRecommendationPersistenceAdapter adapter;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("함께 뛴 사람은 러닝 단계에 들어간 가장 최근 매칭 방 하나에서 찾는다")
    void ranTogetherReadsLatestMatchRoom() {
        // given
        givenLatestRoom(List.of(42L));
        List<RecommendationCandidate> candidates = List.of(
                new RecommendationCandidate(UuidCreator.getTimeOrderedEpoch(), "철수", null));
        givenCandidateQuery(candidates);

        // when
        List<RecommendationCandidate> result = adapter.loadRanTogether(new UserId(userId), 30);

        // then -> 솔로·초대 방과 시작 전에 나간 방은 보지 않는다
        assertThat(result).isEqualTo(candidates);
        verify(roomQuery).setParameter("matchType", RunningRoomType.MATCH);
        verify(roomQuery).setParameter("startedStatuses", List.of(RunningPlayerStatus.RUNNING,
                RunningPlayerStatus.RUNNING_LEFT_PENALTY, RunningPlayerStatus.RUNNING_LEFT_NO_PENALTY,
                RunningPlayerStatus.COMPLETED));
        verify(roomQuery).setMaxResults(1);
        verify(candidateQuery).setParameter("roomId", 42L);
        verify(candidateQuery).setParameter("unavailableTypes", UNAVAILABLE_TYPES);
        verify(candidateQuery).setMaxResults(30);
    }

    @Test
    @DisplayName("러닝 단계에 들어간 매칭 방이 없으면 함께 뛴 사람을 찾지 않는다")
    void noMatchRoomSkipsCandidateQuery() {
        // given
        givenLatestRoom(List.of());

        // when
        List<RecommendationCandidate> result = adapter.loadRanTogether(new UserId(userId), 30);

        // then
        assertThat(result).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(RecommendationCandidate.class));
    }

    @Test
    @DisplayName("페이스 갈래는 기준 ± 허용 차이 범위를 차이가 작은 순으로 남은 자리만큼 읽는다")
    void similarPaceReadsRangeOrderedByDifference() {
        // given
        UUID picked = UuidCreator.getTimeOrderedEpoch();
        givenCandidateQuery(List.of());

        // when
        adapter.loadSimilarPace(new UserId(userId), 330, 20, List.of(picked), 29);

        // then
        String jpql = capturedCandidateJpql();
        assertThat(jpql).contains("o.avgPace BETWEEN :minPace AND :maxPace")
                .contains("AND o.userId NOT IN :excludedUserIds")
                .contains("ORDER BY ABS(o.avgPace - :basePace), o.userId");
        verify(candidateQuery).setParameter("minPace", 310);
        verify(candidateQuery).setParameter("maxPace", 350);
        verify(candidateQuery).setParameter("basePace", 330);
        verify(candidateQuery).setParameter("excludedUserIds", List.of(picked));
        verify(candidateQuery).setParameter("unavailableTypes", UNAVAILABLE_TYPES);
        verify(candidateQuery).setMaxResults(29);
    }

    @Test
    @DisplayName("앞 갈래에서 뽑은 사람이 없으면 NOT IN을 붙이지 않는다")
    void similarPaceWithoutExclusionOmitsNotIn() {
        // given
        givenCandidateQuery(List.of());

        // when
        adapter.loadSimilarPace(new UserId(userId), 330, 20, List.of(), 30);

        // then -> 빈 목록의 NOT IN은 SQL이 깨질 수 있다
        assertThat(capturedCandidateJpql()).doesNotContain("NOT IN");
        verify(candidateQuery, never()).setParameter(eq("excludedUserIds"), any());
    }

    private void givenLatestRoom(List<Long> roomIds) {
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(roomQuery);
        when(roomQuery.setParameter(anyString(), any())).thenReturn(roomQuery);
        when(roomQuery.setMaxResults(anyInt())).thenReturn(roomQuery);
        when(roomQuery.getResultList()).thenReturn(roomIds);
    }

    private void givenCandidateQuery(List<RecommendationCandidate> rows) {
        when(entityManager.createQuery(anyString(), eq(RecommendationCandidate.class))).thenReturn(candidateQuery);
        when(candidateQuery.setParameter(anyString(), any())).thenReturn(candidateQuery);
        when(candidateQuery.setMaxResults(anyInt())).thenReturn(candidateQuery);
        when(candidateQuery.getResultList()).thenReturn(rows);
    }

    private String capturedCandidateJpql() {
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(RecommendationCandidate.class));
        return jpql.getValue();
    }
}
