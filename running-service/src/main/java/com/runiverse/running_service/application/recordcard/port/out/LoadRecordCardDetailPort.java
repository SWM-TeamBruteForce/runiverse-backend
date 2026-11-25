package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

import java.util.Optional;

// 상세(8-1)에 보일 카드 한 장 — 사진·붙은 기록의 수치·구간 경로까지 읽는다. 조회라 잠그지 않는다.
// 볼 수 있는지는 묻지 않는다 — 공개 범위 판단은 유스케이스가 한다
public interface LoadRecordCardDetailPort {

    Optional<RecordCardDetail> loadDetail(RecordCardId recordCardId);
}
