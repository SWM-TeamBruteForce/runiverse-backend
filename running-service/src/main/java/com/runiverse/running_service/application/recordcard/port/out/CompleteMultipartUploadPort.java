package com.runiverse.running_service.application.recordcard.port.out;

public interface CompleteMultipartUploadPort {

    // 나눠 올린 사진을 합친다 — 조각이 발급한 개수만큼 다 올라왔는지 먼저 보고(ListParts), 다 있으면 합친다(Complete).
    // 조각 크기는 업로드 URL 서명이 이미 막았다 — 여기서는 개수만 본다
    MultipartCompletion complete(String imageKey, String uploadId, int expectedPartCount);

    enum MultipartCompletion {
        // 합쳤다 — 이제 일반 객체처럼 HEAD로 확인한다
        COMPLETED,
        // 조각이 덜 올라왔다 — 앱이 빠진 조각을 다시 올리고 다시 확정한다(400 RECORD_CARD_IMAGE_NOT_UPLOADED)
        PARTS_MISSING,
        // 그 UploadId가 S3에 없다 — 이미 합쳤거나(합친 표시를 남기기 전에 끊긴 재시도) 수명 주기 규칙이 지웠다.
        // 어느 쪽인지는 HEAD로 가른다: 객체가 있으면 합쳐진 것이고, 없으면 올리지 않은 것이다
        UPLOAD_NOT_FOUND
    }
}
