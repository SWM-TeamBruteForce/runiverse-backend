package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.relationship.port.out.FriendKeyset;
import com.runiverse.running_service.application.relationship.port.out.FriendRow;
import com.runiverse.running_service.application.relationship.port.out.LoadFriendsPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// 어댑터의 JOIN을 흉내 낸다 — 관계 행에 상대의 온보딩 닉네임·사진과 조회자의 관계를 붙인다.
// UUID는 문자열로 비교한다 — PostgreSQL uuid 비교(바이트 순)와 같고 UUID.compareTo(부호 있는 비교)와 다르다
public class InMemoryFriendListStore implements LoadFriendsPort {

    private static final Comparator<FriendRow> NEWEST_FIRST = Comparator
            .comparing(FriendRow::updatedAt)
            .thenComparing(row -> row.userId().toString())
            .reversed();

    private final InMemoryUserRelationshipStore relationshipStore;
    private final InMemoryOnboardingStore onboardingStore;
    private final InMemoryUserStore userStore;

    public InMemoryFriendListStore(InMemoryUserRelationshipStore relationshipStore,
                                   InMemoryOnboardingStore onboardingStore,
                                   InMemoryUserStore userStore) {
        this.relationshipStore = relationshipStore;
        this.onboardingStore = onboardingStore;
        this.userStore = userStore;
    }

    @Override
    public List<FriendRow> loadGroup(UserId ownerId, UserId viewerId, RelationshipType type,
                                     String nicknameFilter, FriendKeyset after, int limit) {
        return relationshipStore.rowsOwnedBy(ownerId.value()).stream()
                .filter(row -> row.type() == type)
                .map(row -> toFriendRow(row, viewerId))
                .flatMap(Optional::stream)
                .filter(row -> nicknameFilter == null || row.nickname().contains(nicknameFilter))
                .filter(row -> after == null || isAfter(row, after))
                .sorted(NEWEST_FIRST)
                .limit(limit)
                .toList();
    }

    // 온보딩 전인 상대는 INNER JOIN에서 빠진다
    private Optional<FriendRow> toFriendRow(InMemoryUserRelationshipStore.Row row, UserId viewerId) {
        UserId targetId = new UserId(row.targetId());
        return onboardingStore.loadNickname(targetId)
                .map(Nickname::value)
                .map(nickname -> new FriendRow(
                        row.targetId(),
                        nickname,
                        userStore.loadById(targetId)
                                .flatMap(User::getProfileImageKey)
                                .map(ProfileImageKey::value)
                                .orElse(null),
                        row.type(),
                        row.updatedAt(),
                        relationshipStore.rowOf(viewerId.value(), row.targetId())
                                .map(InMemoryUserRelationshipStore.Row::type)
                                .orElse(null)));
    }

    private static boolean isAfter(FriendRow row, FriendKeyset after) {
        int byTime = row.updatedAt().compareTo(after.updatedAt());
        return byTime < 0
                || byTime == 0 && row.userId().toString().compareTo(after.userId().toString()) < 0;
    }
}
