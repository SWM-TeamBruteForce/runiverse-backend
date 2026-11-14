package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecordCardErrorCode implements ErrorCode {
    // 식별자
    INVALID_RECORD_CARD_ID("INVALID_RECORD_CARD_ID", "기록 카드 ID가 올바르지 않습니다."),
    INVALID_RECORD_CARD_IMAGE_ID("INVALID_RECORD_CARD_IMAGE_ID", "기록 카드 사진 ID가 올바르지 않습니다."),
    // 카드 값
    RECORD_CARD_VISIBILITY_REQUIRED("RECORD_CARD_VISIBILITY_REQUIRED", "공개 범위는 필수입니다."),
    RECORD_CARD_ACTIVITY_TYPE_REQUIRED("RECORD_CARD_ACTIVITY_TYPE_REQUIRED", "활동 유형은 필수입니다."),
    RECORD_CARD_MEMO_REQUIRED("RECORD_CARD_MEMO_REQUIRED", "회고 글은 필수입니다."),
    RECORD_CARD_MEMO_TOO_LONG("RECORD_CARD_MEMO_TOO_LONG", "회고 글은 500자 이하여야 합니다."),
    INVALID_DISPLAY_ORDER("INVALID_DISPLAY_ORDER", "카드 순위는 1 이상이거나 삭제 표시(-1)여야 합니다."),
    // 사진 값
    RECORD_CARD_IMAGE_CAPTION_EMPTY("RECORD_CARD_IMAGE_CAPTION_EMPTY", "사진 설명은 비어 있을 수 없습니다. 없으면 생략합니다."),
    RECORD_CARD_IMAGE_CAPTION_TOO_LONG("RECORD_CARD_IMAGE_CAPTION_TOO_LONG", "사진 설명은 100자 이하여야 합니다."),
    // S3 키
    RECORD_CARD_IMAGE_KEY_REQUIRED("RECORD_CARD_IMAGE_KEY_REQUIRED", "사진 키는 필수입니다."),
    RECORD_CARD_IMAGE_KEY_TOO_LONG("RECORD_CARD_IMAGE_KEY_TOO_LONG", "사진 키는 255자를 초과할 수 없습니다."),
    MUSIC_KEY_REQUIRED("MUSIC_KEY_REQUIRED", "음악 키는 필수입니다."),
    MUSIC_KEY_TOO_LONG("MUSIC_KEY_TOO_LONG", "음악 키는 255자를 초과할 수 없습니다."),
    DUPLICATE_MUSIC_KEY("DUPLICATE_MUSIC_KEY", "한 카드 안에서 음악 키가 겹칠 수 없습니다."),
    // 음악 생성 상태
    MUSIC_STATUS_REQUIRED("MUSIC_STATUS_REQUIRED", "음악 생성 상태는 필수입니다."),
    INVALID_MUSIC_RESULT("INVALID_MUSIC_RESULT", "음악 생성 결과는 READY 또는 FAILED여야 합니다."),
    // 사진 구성
    TOO_MANY_RECORD_CARD_IMAGES("TOO_MANY_RECORD_CARD_IMAGES", "사진은 10장 이하만 넣을 수 있습니다."),
    DUPLICATE_RECORD_CARD_IMAGE("DUPLICATE_RECORD_CARD_IMAGE", "같은 사진을 두 번 넣을 수 없습니다."),
    RECORD_CARD_IMAGE_NOT_IN_CARD("RECORD_CARD_IMAGE_NOT_IN_CARD", "이 카드의 사진이 아닙니다."),
    INVALID_THUMBNAIL_INDEX("INVALID_THUMBNAIL_INDEX", "썸네일 위치가 사진 범위를 벗어났습니다."),
    THUMBNAIL_NOT_IN_IMAGES("THUMBNAIL_NOT_IN_IMAGES", "썸네일은 카드 사진 중 하나여야 합니다."),
    // 삭제·되살리기
    RECORD_CARD_DELETED("RECORD_CARD_DELETED", "삭제된 기록 카드입니다."),
    RECORD_CARD_NOT_DELETED("RECORD_CARD_NOT_DELETED", "삭제되지 않은 기록 카드는 되살릴 수 없습니다."),
    DELETED_AT_REQUIRED("DELETED_AT_REQUIRED", "삭제 시각은 필수입니다.");
    private final String code;
    private final String message;
}
