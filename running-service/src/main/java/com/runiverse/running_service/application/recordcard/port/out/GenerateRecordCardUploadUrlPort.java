package com.runiverse.running_service.application.recordcard.port.out;

import java.time.Duration;

public interface GenerateRecordCardUploadUrlPort {

    // 기록 카드 버킷에 한 번에 올릴 presigned PUT URL — 형식·크기를 서명에 넣어 다른 값으로는 올리지 못한다.
    // 유효 시간은 초안과 같게 넘긴다 — 프로필 사진(storage.s3.presigned-url-ttl)과 달리 초안이 살아 있는 동안 올릴 수 있어야 한다
    String generate(String key, String contentType, long sizeBytes, Duration urlTtl);
}
