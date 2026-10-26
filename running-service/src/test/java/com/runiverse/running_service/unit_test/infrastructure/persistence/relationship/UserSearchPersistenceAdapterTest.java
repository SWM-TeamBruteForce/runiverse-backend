package com.runiverse.running_service.unit_test.infrastructure.persistence.relationship;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.port.out.UserSearchRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.infrastructure.persistence.relationship.UserSearchPersistenceAdapter;
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
@DisplayName("사용자 검색 영속성 어댑터 단위 테스트")
public class UserSearchPersistenceAdapterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<UserSearchRow> searchQuery;

    @InjectMocks
    private UserSearchPersistenceAdapter adapter;

    private UUID viewerId;

    @BeforeEach
    void setUp() {
        viewerId = UuidCreator.getTimeOrderedEpoch();
    }

    @Test
    @DisplayName("커서가 없으면 전체 사용자에서 본인을 빼고 닉네임 순으로 읽는다")
    void searchWithoutCursor() {
        // given
        List<UserSearchRow> rows = List.of(new UserSearchRow(
                UuidCreator.getTimeOrderedEpoch(), "철수", null, RelationshipType.FRIEND));
        givenSearchQuery(rows);

        // when
        List<UserSearchRow> result = adapter.search(new UserId(viewerId), "철수", null, 21);

        // then -> 쓰지 않는 조건의 파라미터를 넣으면 Hibernate가 없는 이름이라고 실패한다
        assertThat(result).isEqualTo(rows);
        String jpql = capturedJpql();
        assertThat(jpql)
                .contains("FROM UserOnboardingJpaEntity o")
                .contains("o.userId <> :viewerId")
                .contains("ORDER BY o.nickname")
                .doesNotContain(":afterNickname");
        verify(searchQuery).setParameter("viewerId", viewerId);
        verify(searchQuery).setMaxResults(21);
        verify(searchQuery, never()).setParameter(eq("afterNickname"), any());
    }

    @Test
    @DisplayName("관계는 LEFT JOIN이라 관계 행이 없는 사용자도 결과에 남는다")
    void relationshipIsLeftJoined() {
        // given
        givenSearchQuery(List.of());

        // when
        adapter.search(new UserId(viewerId), "철수", null, 21);

        // then -> INNER JOIN이면 한 번도 관계를 맺지 않은 사람이 검색되지 않는다
        assertThat(capturedJpql())
                .contains("LEFT JOIN UserRelationshipJpaEntity v ON v.userId = :viewerId AND v.targetId = o.userId");
    }

    @Test
    @DisplayName("커서가 있으면 그 닉네임 다음부터 읽는 조건을 붙인다")
    void searchWithCursorAddsAfterCondition() {
        // given
        givenSearchQuery(List.of());

        // when
        adapter.search(new UserId(viewerId), "철수", "철수", 5);

        // then
        assertThat(capturedJpql()).contains("o.nickname > :afterNickname");
        verify(searchQuery).setParameter("afterNickname", "철수");
    }

    @Test
    @DisplayName("검색어는 부분 일치이고 %·_·\\는 글자 그대로 찾는다")
    void searchEscapesWildcards() {
        // given
        givenSearchQuery(List.of());

        // when
        adapter.search(new UserId(viewerId), "a%b_c\\d", null, 21);

        // then -> 이스케이프하지 않으면 "%"만 넣어도 모든 사용자가 걸린다
        assertThat(capturedJpql()).contains("o.nickname LIKE :nicknamePattern ESCAPE '\\'");
        verify(searchQuery).setParameter("nicknamePattern", "%a\\%b\\_c\\\\d%");
    }

    private void givenSearchQuery(List<UserSearchRow> rows) {
        when(entityManager.createQuery(anyString(), eq(UserSearchRow.class))).thenReturn(searchQuery);
        when(searchQuery.setParameter(anyString(), any())).thenReturn(searchQuery);
        when(searchQuery.setMaxResults(anyInt())).thenReturn(searchQuery);
        when(searchQuery.getResultList()).thenReturn(rows);
    }

    private String capturedJpql() {
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(UserSearchRow.class));
        return jpql.getValue();
    }
}
