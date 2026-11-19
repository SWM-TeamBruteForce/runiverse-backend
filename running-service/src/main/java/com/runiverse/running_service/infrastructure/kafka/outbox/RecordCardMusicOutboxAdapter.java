package com.runiverse.running_service.infrastructure.kafka.outbox;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties;
import com.runiverse.running_service.infrastructure.kafka.outbox.OutboxEventJpaEntity.AggregateType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 음악 생성 요청을 아웃박스에 쓴다 — 부르는 트랜잭션(카드 저장)과 함께 커밋된다. Kafka로 보내는 것은 Debezium이다.
// 본문 키 이름(snake_case)·시각 형식은 AI 서버와 맞춘 약속이라 바꾸지 않는다
@Component
@RequiredArgsConstructor
public class RecordCardMusicOutboxAdapter implements RequestRecordCardMusicPort {

    static final String TOPIC = "record-card.music.requested";

    private final EntityManager entityManager;
    private final KafkaNamingProperties kafkaNamingProperties;

    @Override
    public void request(RecordCardMusicRequest request) {
        // 이벤트 ID는 Kafka 헤더 id가 된다 — 받는 쪽 inbox가 이 값으로 같은 이벤트를 거른다
        entityManager.persist(OutboxEventJpaEntity.create(
                UuidCreator.getTimeOrderedEpoch(), AggregateType.RECORD_CARD, request.recordCardId().toString(),
                kafkaNamingProperties.topic(TOPIC), payload(request)));
    }

    // 값이 없어도 키를 빼지 않는다 — LinkedHashMap은 null 값을 그대로 둔다
    private static Map<String, Object> payload(RecordCardMusicRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", request.userId().toString());
        payload.put("record_card_id", request.recordCardId().toString());
        payload.put("memo", request.memo());
        payload.put("activity_type", request.activityType().name());
        payload.put("running_summary", runningSummary(request.runningSummary()));
        payload.put("photos", photos(request.photos()));
        return payload;
    }

    private static Map<String, Object> runningSummary(RunningSummary summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total_distance", summary.totalDistance());
        map.put("total_duration", summary.totalDuration());
        map.put("avg_pace", summary.avgPace());
        map.put("avg_cadence", summary.avgCadence());
        map.put("start_at", startAt(summary));
        map.put("weather_code", summary.weatherCode());
        map.put("temperature", summary.temperature());
        map.put("total_elevation_gain", summary.totalElevationGain());
        return map;
    }

    // 이 토픽만 오프셋을 붙인다 — 기록 시각은 앱 시간대(KST) 값이라 그 시간대로 +09:00을 붙인다.
    // 초 아래는 버린다. ISO 형식은 초가 0이어도 :00을 쓴다(OffsetDateTime.toString은 생략한다)
    private static String startAt(RunningSummary summary) {
        return summary.startAt().truncatedTo(ChronoUnit.SECONDS)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static List<Map<String, Object>> photos(List<Photo> photos) {
        return photos.stream().map(photo -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("record_card_image_id", photo.recordCardImageId().toString());
            map.put("image_key", photo.imageKey());
            map.put("caption", photo.caption());
            return map;
        }).toList();
    }
}
