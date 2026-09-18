package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.LocalDateTime;
import java.util.List;

public interface LoadMatchCandidatesPort {

    // 같은 슬롯·거리에서 모집 중이고 자리가 남은 방(erd 인덱스 참고).
    // 솔로·초대 방은 type으로 배제되고 최종 자격은 애그리거트가 다시 판정한다.
    // userId는 거르는 조건이 아니라 순위 재료다 — 신청자가 그 방을 나간 횟수를 함께 읽는다
    List<MatchCandidate> loadCandidates(UserId userId, LocalDateTime startAt, int targetDistanceMeters);
}
