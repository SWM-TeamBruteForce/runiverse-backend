package com.runiverse.running_service.application.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 기록 카드 — 기록이 없거나 남의 기록이면 ResourceErrorCode.NOT_FOUND를 쓴다.
// 남의 기록이 있는지 없는지를 알려 주지 않으려고 404는 가르지 않는다
@Getter
@RequiredArgsConstructor
public enum RecordCardErrorCode implements ErrorCode {
    // 이 기록에 카드가 이미 있다 — 기록과 카드는 1:1이다. 지운 카드는 행이 없어(하드 삭제) 여기 걸리지 않는다
    RECORD_CARD_ALREADY_EXISTS("RECORD_CARD_ALREADY_EXISTS", "이미 기록 카드가 있는 러닝입니다."),
    // 사진이 최대 크기(record-card.draft.max-image-size)를 넘는다 — 운영값이라 Request DTO가 못 보고 유스케이스가 본다.
    // 클라에는 요청 검증 실패와 같은 코드로 보인다. 실제 문구는 예외가 운영값으로 만든다
    RECORD_CARD_IMAGE_TOO_LARGE("INVALID_REQUEST", "이미지가 업로드할 수 있는 크기를 넘었습니다."),
    // 확정(9-2): 초안에 적힌 사진이 S3에 없다 — 안 올렸거나, 나눠 올린 조각이 빠졌다. 앱은 빠진 것만 다시 올리고 다시 확정한다
    RECORD_CARD_IMAGE_NOT_UPLOADED("RECORD_CARD_IMAGE_NOT_UPLOADED", "업로드되지 않은 이미지입니다."),
    // 확정(9-2): 올라간 파일의 크기·형식이 작성 요청과 다르다 — 서명이 막지만 마지막으로 한 번 더 본다. 9-1부터 다시 한다
    INVALID_RECORD_CARD_IMAGE("INVALID_RECORD_CARD_IMAGE", "기록 카드 사진이 올바르지 않습니다."),
    // 확정(9-2): 같은 초안의 확정이 이미 처리 중이거나, 같은 사람의 다른 카드 확정과 순위가 겹쳤다 —
    // 기다리거나 다시 시도하지 않고 돌려보낸다. 다시 부를지는 앱이 정한다
    RECORD_CARD_DRAFT_IN_PROGRESS("RECORD_CARD_DRAFT_IN_PROGRESS", "기록 카드를 저장하고 있습니다. 잠시 후 다시 시도해 주세요.");
    private final String code;
    private final String message;
}
