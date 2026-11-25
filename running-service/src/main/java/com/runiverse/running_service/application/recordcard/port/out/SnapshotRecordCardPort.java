package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

// 바뀌기 직전 카드(사진 포함)를 이력으로 남긴다 — 신고·분쟁 대응용이라 API로 내려주지 않는다.
// 카드 쓰기와 같은 트랜잭션에서, 기록 행을 잠그고 카드를 읽은 뒤에 부른다(잠금 안이라 직전 모습과 순서가 어긋나지 않는다).
// 저장소에 있는 지금 모습을 남긴다 — 도메인에서 수정을 계산한 뒤여도 저장(UpdateRecordCardPort)보다 먼저 부르면 직전 모습이다.
// 그래서 바뀐 것이 있을 때만 남길 수 있다
public interface SnapshotRecordCardPort {

    void snapshot(RecordCardId recordCardId, RecordCardSnapshotReason reason);
}
