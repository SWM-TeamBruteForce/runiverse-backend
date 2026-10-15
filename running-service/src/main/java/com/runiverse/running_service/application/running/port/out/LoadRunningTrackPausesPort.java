package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.List;

// 트랙 일시정지 표시(MarkRunningTrackPausedPort)를 읽는다.
// 실패를 삼키지 않는다: 부르는 쪽이 로그를 남기고 이어 갈지 정한다
public interface LoadRunningTrackPausesPort {

    // 오름차순. 표시가 없으면 빈 목록
    List<Long> loadPausedSequences(Long runningRoomId, UserId userId);
}
