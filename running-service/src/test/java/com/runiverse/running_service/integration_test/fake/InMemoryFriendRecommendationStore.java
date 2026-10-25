package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.relationship.port.out.LoadFriendRecommendationsPort;
import com.runiverse.running_service.application.relationship.port.out.RecommendationCandidate;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 가장 최근 매칭 방을 고르는 판정은 어댑터 JPQL이라 E2E가 확인한다 — 여기서는 그 방의 참가자만 심는다.
// 제외 규칙(본인·친구·대기 중 요청·온보딩 전)과 페이스 범위는 실제 저장소 값으로 흉내 낸다
public class InMemoryFriendRecommendationStore implements LoadFriendRecommendationsPort {

    private static final Set<RelationshipType> UNAVAILABLE_TYPES = Set.of(
            RelationshipType.FRIEND, RelationshipType.FRIEND_REQUEST_SENT, RelationshipType.FRIEND_REQUEST_RECEIVED);

    private final InMemoryUserRelationshipStore relationshipStore;
    private final InMemoryOnboardingStore onboardingStore;
    private final InMemoryUserStore userStore;
    private final Map<UUID, List<UUID>> latestMatchRunners = new HashMap<>();

    public InMemoryFriendRecommendationStore(InMemoryUserRelationshipStore relationshipStore,
                                             InMemoryOnboardingStore onboardingStore,
                                             InMemoryUserStore userStore) {
        this.relationshipStore = relationshipStore;
        this.onboardingStore = onboardingStore;
        this.userStore = userStore;
    }

    // 테스트 준비 — userId가 가장 최근 매칭 방에서 함께 러닝 단계에 들어간 사람들
    public void seedLatestMatchRunners(UUID userId, UUID... runnerIds) {
        latestMatchRunners.put(userId, List.of(runnerIds));
    }

    @Override
    public List<RecommendationCandidate> loadRanTogether(UserId userId, int limit) {
        return latestMatchRunners.getOrDefault(userId.value(), List.of()).stream()
                .filter(runnerId -> isAvailable(userId.value(), runnerId))
                .map(this::candidateOf)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(candidate -> candidate.userId().toString()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<RecommendationCandidate> loadSimilarPace(UserId userId, int baseSecondsPerKm,
                                                         int toleranceSecondsPerKm,
                                                         Collection<UUID> excludedUserIds, int limit) {
        return onboardingStore.findAll().stream()
                .filter(row -> Math.abs(row.getAvgPace().secondPerKm() - baseSecondsPerKm) <= toleranceSecondsPerKm)
                .filter(row -> !excludedUserIds.contains(row.getUserId().value()))
                .filter(row -> isAvailable(userId.value(), row.getUserId().value()))
                .sorted(Comparator.<InMemoryOnboardingStore.OnboardingRow>comparingInt(
                                row -> Math.abs(row.getAvgPace().secondPerKm() - baseSecondsPerKm))
                        .thenComparing(row -> row.getUserId().value().toString()))
                .map(row -> candidateOf(row.getUserId().value()))
                .flatMap(Optional::stream)
                .limit(limit)
                .toList();
    }

    private boolean isAvailable(UUID userId, UUID targetId) {
        return !userId.equals(targetId)
                && relationshipStore.typeOf(userId, targetId).map(type -> !UNAVAILABLE_TYPES.contains(type)).orElse(true);
    }

    // 온보딩 전인 사람은 닉네임이 없어 INNER JOIN에서 빠진다
    private Optional<RecommendationCandidate> candidateOf(UUID targetId) {
        return onboardingStore.nicknameOf(targetId)
                .map(nickname -> new RecommendationCandidate(
                        targetId,
                        nickname,
                        userStore.loadById(new UserId(targetId))
                                .flatMap(User::getProfileImageKey)
                                .map(ProfileImageKey::value)
                                .orElse(null)));
    }
}
