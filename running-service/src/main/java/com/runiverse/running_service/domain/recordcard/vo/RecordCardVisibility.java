package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.common.vo.RelationLevel;

// 카드별 공개 범위 — 기본값은 두지 않는다(작성 화면이 고른다).
// 공개 범위마다 볼 수 있는 최소 관계 등급이 있다 — 차단(BLOCKED)은 가장 낮은 등급이라 어느 범위도 볼 수 없다
public enum RecordCardVisibility {
    // 작성자와 친구인 사람만
    FRIENDS(RelationLevel.FRIEND),
    // 누구나 — 차단 관계만 빼고
    PUBLIC(RelationLevel.OTHER),
    // 작성자만
    PRIVATE(RelationLevel.SELF);

    private final RelationLevel required;

    RecordCardVisibility(RelationLevel required) {
        this.required = required;
    }

    // 이 공개 범위의 카드를 그 관계 등급의 사람이 볼 수 있는가 — 볼 수 없으면 조회는 404로 숨긴다
    public boolean isVisibleTo(RelationLevel level) {
        return level.isAtLeast(required);
    }
}
