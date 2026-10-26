package com.runiverse.running_service.infrastructure.persistence.relationship;

import com.runiverse.running_service.application.relationship.port.out.SearchUsersPort;
import com.runiverse.running_service.application.relationship.port.out.UserSearchRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.persistence.common.LikePatterns;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 온보딩·유저 테이블에서 찾지만 결과마다 조회자와의 관계를 실어 relationship에 둔다
@Component
@RequiredArgsConstructor
public class UserSearchPersistenceAdapter implements SearchUsersPort {

    // 온보딩 전인 사람은 닉네임이 없어 출발 테이블에 없다. 관계는 LEFT JOIN이라 관계가 없는 사람도 빠지지 않는다.
    // 부분 일치라 UNIQUE(nickname) 인덱스를 타지 못해 user_onboardings를 훑는다 — 느려지면 pg_trgm 인덱스나
    // 앞부분 일치(LikePatterns에 패턴 추가)로 바꾼다
    private static final String SELECT_USERS = """
            SELECT NEW com.runiverse.running_service.application.relationship.port.out.UserSearchRow(
                o.userId, o.nickname, u.profileImageKey, v.type)
            FROM UserOnboardingJpaEntity o
            JOIN UserJpaEntity u ON u.userId = o.userId
            LEFT JOIN UserRelationshipJpaEntity v ON v.userId = :viewerId AND v.targetId = o.userId
            WHERE o.userId <> :viewerId
              AND o.nickname LIKE :nicknamePattern ESCAPE '\\'
            """;
    // 닉네임이 UNIQUE라 닉네임만으로 다음 위치가 정해진다
    private static final String AFTER_CONDITION = """
              AND o.nickname > :afterNickname
            """;
    private static final String ORDER_USERS = """
            ORDER BY o.nickname
            """;
    private final EntityManager entityManager;

    // 조건을 쿼리 문자열로 붙인다 — ":param IS NULL OR ..."로 쓰면 null 파라미터의 타입을 PostgreSQL이 추론하지 못한다
    @Override
    public List<UserSearchRow> search(UserId viewerId, String nicknameFilter, String afterNickname, int limit) {
        String jpql = SELECT_USERS
                + (afterNickname == null ? "" : AFTER_CONDITION)
                + ORDER_USERS;

        TypedQuery<UserSearchRow> query = entityManager.createQuery(jpql, UserSearchRow.class)
                .setParameter("viewerId", viewerId.value())
                .setParameter("nicknamePattern", LikePatterns.containing(nicknameFilter))
                .setMaxResults(limit);
        if (afterNickname != null) {
            query.setParameter("afterNickname", afterNickname);
        }
        return query.getResultList();
    }
}
