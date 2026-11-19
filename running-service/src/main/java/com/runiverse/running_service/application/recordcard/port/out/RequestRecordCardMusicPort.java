package com.runiverse.running_service.application.recordcard.port.out;

// AI 서버에 음악 생성을 요청한다 — 부르는 트랜잭션과 함께 커밋된다.
// 롤백되면 요청도 남지 않고, 커밋되면 빠짐없이 전달된다(어댑터는 아웃박스에 쓰고 CDC가 Kafka로 보낸다).
// 같은 요청이 두 번 전달될 수 있다 — 받는 쪽이 이벤트 ID로 거른다
public interface RequestRecordCardMusicPort {

    void request(RecordCardMusicRequest request);
}
