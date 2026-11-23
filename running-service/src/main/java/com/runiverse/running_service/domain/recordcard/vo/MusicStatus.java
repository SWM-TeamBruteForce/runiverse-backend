package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;

// 음악 하나의 생성 상태 — 생성 완료 알림·수정(설명이 바뀐 사진)·다시 만들기로 바뀐다
public enum MusicStatus {
    // 생성 중 — 카드를 저장하거나 다시 만들 때 이 상태로 시작한다. 저장소에 객체가 아직 없고 key도 없다
    GENERATING,
    // 생성 완료 — AI 서버가 올린 객체의 key를 받았다. 재생할 수 있다
    COMPLETED,
    // 생성 실패 — 다시 만들기·설명을 고친 수정이 GENERATING으로 되돌린다. 기본곡은 되돌리지 않아도 뒤에 온 결과를 따른다
    FAILED;

    // 완료 알림의 결과를 받는다 — 결과는 COMPLETED·FAILED뿐이다
    public static MusicStatus result(MusicStatus result) {
        if (result != COMPLETED && result != FAILED) {
            throw new InvalidMusicResultException();
        }
        return result;
    }

    public boolean isGenerating() {
        return this == GENERATING;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}
