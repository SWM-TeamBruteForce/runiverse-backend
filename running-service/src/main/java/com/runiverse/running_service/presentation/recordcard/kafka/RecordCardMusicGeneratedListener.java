package com.runiverse.running_service.presentation.recordcard.kafka;

import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardMusicUsecase;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 음악 생성 완료 이벤트를 받는다 — 형식 검사·Command 변환은 RecordCardMusicGeneratedMessageMapper가 하고 usecase를 부른다.
// 계약 위반은 Mapper가 건너뛴다 — 다시 받아도 계속 실패해 파티션이 멈추기 때문이다.
// usecase의 예외(DB 오류)는 그대로 올린다 — 오프셋을 커밋하지 않아 다시 받는다(오류 처리기는 KafkaConsumerConfig).
// 로컬·E2E는 브로커에 닿지 않아 kafka.consumer.enabled=false로 이 빈을 만들지 않는다
@Component
@ConditionalOnBooleanProperty("kafka.consumer.enabled")
@RequiredArgsConstructor
public class RecordCardMusicGeneratedListener {

    private final RecordCardMusicGeneratedMessageMapper recordCardMusicGeneratedMessageMapper;
    private final CompleteRecordCardMusicUsecase completeRecordCardMusicUsecase;

    // 인스턴스가 여럿이어도 그룹 하나로 받는다 — 이벤트 하나는 한 인스턴스만 처리한다
    @KafkaListener(topics = "${kafka.env}.record-card.music.generated",
            groupId = "${kafka.env}.running-service.record-card-music")
    public void onMessage(ConsumerRecord<String, String> record) {
        recordCardMusicGeneratedMessageMapper.toCommand(record).ifPresent(completeRecordCardMusicUsecase::handle);
    }
}
