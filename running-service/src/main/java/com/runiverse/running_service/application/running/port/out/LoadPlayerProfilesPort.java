package com.runiverse.running_service.application.running.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// 사용자 정보를 running이 자기 포트로 읽는다 — LoadUserWeightPort·LoadUserAvgPacePort와 같은 결이다
public interface LoadPlayerProfilesPort {

    // 탈퇴자는 users 행이 지워져 결과에서 빠진다 —
    // 호출자가 "없으면 탈퇴"로 판정한다(api-spec §0 탈퇴 유저 표시)
    Map<UUID, PlayerProfile> loadProfiles(Collection<UUID> userIds);
}
