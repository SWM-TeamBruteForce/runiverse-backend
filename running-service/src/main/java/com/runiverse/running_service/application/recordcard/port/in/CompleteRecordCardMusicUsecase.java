package com.runiverse.running_service.application.recordcard.port.in;

import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand;

// 음악 생성 완료를 반영한다 — 같은 이벤트(eventId)는 한 번만 처리한다.
// 예외가 나면 반영하지 않은 것이다(트랜잭션 롤백) — 부르는 쪽은 다시 받아 다시 부른다
public interface CompleteRecordCardMusicUsecase {

    void handle(CompleteRecordCardMusicCommand command);
}
