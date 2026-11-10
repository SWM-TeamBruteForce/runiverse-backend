package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;

// 음악 하나의 생성 상태 — 생성 완료 알림으로만 바뀌고, PENDING에서 한 번만 바뀐다
public enum MusicStatus {
    // 생성 중 — 저장소에 객체가 아직 없다
    PENDING,
    // 생성 완료 — key 자리에 객체가 올라왔다
    READY,
    // 생성 실패 — 다시 만드는 경로는 아직 없다
    FAILED;

    // 완료 알림의 결과를 받는다 — 결과는 READY·FAILED뿐이다
    public static MusicStatus result(MusicStatus result) {
        if (result != READY && result != FAILED) {
            throw new InvalidMusicResultException();
        }
        return result;
    }

    public boolean isPending() {
        return this == PENDING;
    }
}
