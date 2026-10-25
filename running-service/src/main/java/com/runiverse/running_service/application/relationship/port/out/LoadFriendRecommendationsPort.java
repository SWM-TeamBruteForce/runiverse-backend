package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 두 갈래 모두 친구 요청을 보낼 수 있는 사람만 돌려준다 — 본인, 친구, 어느 방향이든 요청이 대기 중인 사람,
// 온보딩 전인 사람(탈퇴자 포함)은 담지 않는다
public interface LoadFriendRecommendationsPort {

    // 본인이 러닝 단계에 들어간 가장 최근 매칭 방에서 함께 러닝 단계에 들어간 사람 — userId 순
    List<RecommendationCandidate> loadRanTogether(UserId userId, int limit);

    // 평균 페이스가 baseSecondsPerKm ± toleranceSecondsPerKm 안인 사람 — 차이가 작은 순, 같으면 userId 순.
    // excludedUserIds는 앞 갈래에서 이미 뽑은 사람이다
    List<RecommendationCandidate> loadSimilarPace(UserId userId, int baseSecondsPerKm, int toleranceSecondsPerKm,
                                                  Collection<UUID> excludedUserIds, int limit);
}
