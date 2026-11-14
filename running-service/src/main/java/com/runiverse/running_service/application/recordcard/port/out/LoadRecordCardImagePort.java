package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.application.common.port.out.UploadedImage;

import java.util.Optional;

public interface LoadRecordCardImagePort {

    // 기록 카드 버킷에 올라간 사진의 크기·형식 — 없으면 비어 있다(아직 안 올렸거나 key가 틀렸다).
    // 확정(9-2)이 초안에 적힌 값과 대조한다 — 클라가 올렸다는 말을 믿지 않는다
    Optional<UploadedImage> load(String imageKey);
}
