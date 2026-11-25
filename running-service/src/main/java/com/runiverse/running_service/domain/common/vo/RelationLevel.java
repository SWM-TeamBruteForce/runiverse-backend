package com.runiverse.running_service.domain.common.vo;

// 보는 사람이 상대(카드 작성자·프로필 주인 등)에게 어떤 사람인가를 등급으로 정리한 값 — SELF > FRIEND > OTHER > BLOCKED.
// 각 기능의 공개 규칙이 "필요한 등급 이상이면 볼 수 있다"로 쓴다. 시스템 역할(관리자 등)이 아니다 — 상대마다 달라진다.
// RelationshipType을 그대로 쓰지 않는다 — 그쪽은 행 주인 한 방향의 저장 상태라 "어느 쪽이 막았든 차단"을 담지 못한다
public enum RelationLevel {
    // 어느 쪽이 막았든 차단 관계 — 어떤 공개 범위도 볼 수 없다
    BLOCKED,
    // 친구도 차단도 아닌 사람 — 친구 요청을 주고받는 중도 여기다
    OTHER,
    // 상대와 친구
    FRIEND,
    // 상대 본인
    SELF;

    // 선언 순서가 등급 순서다 — 순서를 바꾸면 공개 규칙이 바뀐다
    public boolean isAtLeast(RelationLevel required) {
        return compareTo(required) >= 0;
    }
}
