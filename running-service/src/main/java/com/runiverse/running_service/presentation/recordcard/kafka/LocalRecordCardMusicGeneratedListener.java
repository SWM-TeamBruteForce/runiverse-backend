package com.runiverse.running_service.presentation.recordcard.kafka;

import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardMusicUsecase;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 로컬 전용 음악 완료 수신 — Kafka 대신 LocalAiMusicAdapter가 낸 Spring 이벤트(ConsumerRecord 그대로)를 받는다.
// 받는 길만 다르고 RecordCardMusicGeneratedListener와 같은 Mapper·usecase를 탄다(inbox 기록·음악 저장까지 운영과 같다).
// 네 조건이 모두 맞아야 만든다: local 프로필 · Kafka 구독 꺼짐(test·deploy는 켜져 있다) ·
// KAFKA_ENV=dev(로컬 앱을 test DB에 붙이면 test다) · local-ai 설정(E2E는 끈다)
@Component
@Profile("local")
@ConditionalOnBooleanProperty(name = "kafka.consumer.enabled", havingValue = false)
@ConditionalOnProperty(name = "kafka.env", havingValue = "dev")
@ConditionalOnBooleanProperty("record-card.music.local-ai.enabled")
@RequiredArgsConstructor
public class LocalRecordCardMusicGeneratedListener {

    private final RecordCardMusicGeneratedMessageMapper recordCardMusicGeneratedMessageMapper;
    private final CompleteRecordCardMusicUsecase completeRecordCardMusicUsecase;

    // 보낸 스케줄러 스레드에서 그대로 돈다 — usecase 예외는 보낸 쪽(LocalAiMusicAdapter)이 로그로 남긴다
    @EventListener
    public void onMessage(ConsumerRecord<String, String> record) {
        recordCardMusicGeneratedMessageMapper.toCommand(record).ifPresent(completeRecordCardMusicUsecase::handle);
    }
}
