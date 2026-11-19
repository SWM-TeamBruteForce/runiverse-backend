package com.runiverse.running_service.application.common.port.out;

// 받은 이벤트를 inbox에 남긴다 — 처음 받은 이벤트면 true, 이미 처리한 이벤트면 false(아무것도 남기지 않는다).
// 처리와 같은 트랜잭션에서 부른다 — 처리가 롤백되면 기록도 남지 않아, 다시 받았을 때 처리된다.
// 키는 (topic, eventId)다 — 토픽마다 이벤트 키를 정하는 쪽이 달라 겹칠 수 있다
public interface RecordInboxEventPort {

    boolean recordIfAbsent(String topic, String eventId, String aggregateId, String payload);
}
