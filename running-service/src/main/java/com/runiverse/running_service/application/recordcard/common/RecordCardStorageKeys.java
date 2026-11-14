package com.runiverse.running_service.application.recordcard.common;

import java.util.UUID;

// 기록 카드 S3 key 규칙 — 작성자 폴더 아래 카드마다, 사진마다 폴더를 둔다:
// record-cards/{userId}/{recordCardId}/{recordCardImageId}/{사용자가 올린 파일 이름}
// key는 서버만 만든다 — 앱이 key를 보내는 경로가 없어 소유자 대조가 필요 없다(초안이 key를 들고 있다)
public final class RecordCardStorageKeys {

    private static final String ROOT = "record-cards";

    private RecordCardStorageKeys() {
    }

    // 끝의 /까지 포함해야 userId 뒤에 다른 문자가 붙은 key가 섞이지 않는다 — 탈퇴 시 지우는 범위도 이 폴더다
    public static String prefixOf(UUID userId) {
        return "%s/%s/".formatted(ROOT, userId);
    }

    // 파일 이름은 사용자가 올린 그대로 둔다 — 사진마다 폴더(사진 ID)가 달라 같은 이름도 겹치지 않는다.
    // 폴더를 벗어나는 문자(/·\·제어 문자)와 길이는 Request DTO가 이미 걸렀다
    public static String imageKey(UUID userId, UUID recordCardId, UUID recordCardImageId, String fileName) {
        return "%s%s/%s/%s".formatted(prefixOf(userId), recordCardId, recordCardImageId, fileName);
    }
}
