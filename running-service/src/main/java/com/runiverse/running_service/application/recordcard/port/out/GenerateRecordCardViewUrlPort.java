package com.runiverse.running_service.application.recordcard.port.out;

// 기록 카드의 사진·음악을 앱이 받을 presigned GET URL로 바꾼다 — 앱은 S3 권한이 없어 key로는 열 수 없다.
// 사진은 기록 카드 버킷, 음악은 AI 서버가 올리는 음악 버킷이라 메서드를 나눈다
public interface GenerateRecordCardViewUrlPort {

    String imageUrl(String imageKey);

    String musicUrl(String musicKey);
}
