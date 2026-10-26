package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.relationship.port.out.SearchUsersPort;
import com.runiverse.running_service.application.relationship.port.out.UserSearchRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;

import java.util.Comparator;
import java.util.List;

// 어댑터의 JOIN을 흉내 낸다 — user_onboardings 전체에서 출발해 사진과 조회자의 관계를 붙인다.
// 닉네임은 String 순서로 비교한다 — DB 콜레이션과 대소문자·기호 순서가 다를 수 있어 테스트는 소문자 영문·숫자만 쓴다
public class InMemoryUserSearchStore implements SearchUsersPort {

    private final InMemoryOnboardingStore onboardingStore;
    private final InMemoryUserStore userStore;
    private final InMemoryUserRelationshipStore relationshipStore;

    public InMemoryUserSearchStore(InMemoryOnboardingStore onboardingStore,
                                   InMemoryUserStore userStore,
                                   InMemoryUserRelationshipStore relationshipStore) {
        this.onboardingStore = onboardingStore;
        this.userStore = userStore;
        this.relationshipStore = relationshipStore;
    }

    @Override
    public List<UserSearchRow> search(UserId viewerId, String nicknameFilter, String afterNickname, int limit) {
        return onboardingStore.findAll().stream()
                .filter(row -> !row.getUserId().equals(viewerId))
                .filter(row -> row.getNickname().value().contains(nicknameFilter))
                .filter(row -> afterNickname == null || row.getNickname().value().compareTo(afterNickname) > 0)
                .sorted(Comparator.comparing(row -> row.getNickname().value()))
                .limit(limit)
                .map(row -> toSearchRow(row, viewerId))
                .toList();
    }

    // 관계 행이 없으면 LEFT JOIN처럼 null이다
    private UserSearchRow toSearchRow(InMemoryOnboardingStore.OnboardingRow row, UserId viewerId) {
        return new UserSearchRow(
                row.getUserId().value(),
                row.getNickname().value(),
                userStore.loadById(row.getUserId())
                        .flatMap(User::getProfileImageKey)
                        .map(ProfileImageKey::value)
                        .orElse(null),
                relationshipStore.rowOf(viewerId.value(), row.getUserId().value())
                        .map(InMemoryUserRelationshipStore.Row::type)
                        .orElse(null));
    }
}
