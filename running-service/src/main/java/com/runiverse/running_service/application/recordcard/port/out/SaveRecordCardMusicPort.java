package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.RecordCard;

public interface SaveRecordCardMusicPort {

    // 음악 생성 결과만 쓴다 — 기본곡과 사진마다의 상태·key. 글·공개 범위·사진 구성은 건드리지 않는다.
    // loadForUpdate로 잠그고 읽은 카드를 같은 트랜잭션에서 넘긴다
    void saveMusic(RecordCard recordCard);
}
