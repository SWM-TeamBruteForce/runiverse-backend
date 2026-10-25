package com.runiverse.running_service.application.relationship.query.recommendation;

import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.relationship.port.in.GetFriendRecommendationsUsecase;
import com.runiverse.running_service.application.relationship.port.out.LoadFriendRecommendationsPort;
import com.runiverse.running_service.application.relationship.port.out.RecommendationCandidate;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetFriendRecommendationsHandler implements GetFriendRecommendationsUsecase {

    private final LoadFriendRecommendationsPort loadFriendRecommendationsPort;
    private final LoadUserAvgPacePort loadUserAvgPacePort;
    private final GenerateViewUrlPort generateViewUrlPort;
    private final FriendRecommendationProperties properties;

    @Override
    public GetFriendRecommendationsResult handle(GetFriendRecommendationsQuery query) {
        UserId userId = new UserId(query.userId());
        int maxCount = properties.maxCount();

        // 1. 함께 뛴 사람을 먼저 채운다
        List<RecommendationCandidate> ranTogether = loadFriendRecommendationsPort.loadRanTogether(userId, maxCount);
        List<GetFriendRecommendationsResult.Recommendation> recommendations = new ArrayList<>();
        ranTogether.forEach(candidate -> recommendations.add(
                toRecommendation(candidate, RecommendationReason.RAN_TOGETHER)));

        // 2. 남은 자리를 페이스가 비슷한 사람으로 채운다 — 두 갈래에 모두 걸리면 앞 갈래로 한 번만 낸다.
        //    온보딩 전이면 기준 페이스가 없어 이 갈래는 비어 있다
        int remaining = maxCount - ranTogether.size();
        Optional<Pace> myPace = loadUserAvgPacePort.loadAvgPace(userId);
        if (remaining > 0 && myPace.isPresent()) {
            List<UUID> alreadyPicked = ranTogether.stream().map(RecommendationCandidate::userId).toList();
            loadFriendRecommendationsPort.loadSimilarPace(
                            userId, myPace.get().secondsPerKm(), properties.paceToleranceSecondsPerKm(),
                            alreadyPicked, remaining)
                    .forEach(candidate -> recommendations.add(
                            toRecommendation(candidate, RecommendationReason.SIMILAR_PACE)));
        }

        return new GetFriendRecommendationsResult(recommendations);
    }

    private GetFriendRecommendationsResult.Recommendation toRecommendation(
            RecommendationCandidate candidate, RecommendationReason reason) {
        return new GetFriendRecommendationsResult.Recommendation(
                candidate.userId(),
                candidate.nickname(),
                candidate.profileImageKey() == null ? null : generateViewUrlPort.generate(candidate.profileImageKey()),
                reason);
    }
}
