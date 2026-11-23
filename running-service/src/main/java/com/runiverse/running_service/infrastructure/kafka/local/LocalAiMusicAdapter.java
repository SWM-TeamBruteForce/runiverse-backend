package com.runiverse.running_service.infrastructure.kafka.local;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties;
import com.runiverse.running_service.infrastructure.kafka.outbox.RecordCardMusicOutboxAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 로컬 전용 가짜 AI 서버 — 로컬엔 Debezium·Kafka·AI 서버가 없어 음악이 계속 GENERATING이라, 그 자리만 대신한다.
// 아웃박스는 진짜 어댑터가 그대로 쓰고, 커밋 뒤 DELAY가 지나면 AI 서버가 보낼 완료 메시지(api-spec 9-5 성공 형식)를
// ConsumerRecord 그대로 Spring 이벤트로 낸다 — LocalRecordCardMusicGeneratedListener가 받아 운영과 같은 Mapper·usecase를 탄다.
// 네 조건이 모두 맞아야 만든다: local 프로필 · Kafka 구독 꺼짐(test·deploy는 켜져 있다) ·
// KAFKA_ENV=dev(로컬 앱을 test DB에 붙이면 test다) · local-ai 설정(E2E는 끈다)
@Slf4j
@Component
@Primary
@Profile("local")
@ConditionalOnBooleanProperty(name = "kafka.consumer.enabled", havingValue = false)
@ConditionalOnProperty(name = "kafka.env", havingValue = "dev")
@ConditionalOnBooleanProperty("record-card.music.local-ai.enabled")
public class LocalAiMusicAdapter implements RequestRecordCardMusicPort {

    // 앱에서 GENERATING → COMPLETED로 바뀌는 걸 볼 수 있게 둔다
    static final Duration DELAY = Duration.ofSeconds(5);
    static final String GENERATED_TOPIC = "record-card.music.generated";
    private static final String EVENT_ID_HEADER = "id";
    private static final double BASE_MUSIC_SECONDS = 30.0;
    private static final double PHOTO_MUSIC_SECONDS = 10.0;
    // 이벤트 타입을 ConsumerRecord<String, String>으로 밝혀 보낸다 — 그냥 보내면 실행 중엔 제네릭이 지워져
    // ConsumerRecord<?, ?>로만 보여, 수신기의 @EventListener(ConsumerRecord<String, String>)가 받지 않는다
    private static final ResolvableType RECORD_TYPE =
            ResolvableType.forClassWithGenerics(ConsumerRecord.class, String.class, String.class);

    private final RecordCardMusicOutboxAdapter recordCardMusicOutboxAdapter;
    private final TaskScheduler taskScheduler;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JsonMapper jsonMapper;
    private final KafkaNamingProperties kafkaNamingProperties;
    // 모든 음악(기본곡·사진곡)에 배정할 S3 key — 로컬 버킷에 미리 올려 둔 파일 하나다.
    // 서버는 AI가 준 key를 그대로 저장·조회하므로 key 규칙을 흉내 내지 않는다
    private final String localMusicKey;

    // @ConfigurationProperties로 두지 않는다 — 스캔되어 모든 환경에서 바인딩되면 test·deploy에도 이 값이 필요해진다.
    // 이 빈은 로컬에서만 만들어지므로 여기서 읽고, 비어 있으면 로컬 기동을 막는다(조용히 key 없는 결과를 보내지 않게)
    public LocalAiMusicAdapter(RecordCardMusicOutboxAdapter recordCardMusicOutboxAdapter, TaskScheduler taskScheduler,
                               ApplicationEventPublisher applicationEventPublisher, JsonMapper jsonMapper,
                               KafkaNamingProperties kafkaNamingProperties,
                               @Value("${record-card.music.local-ai.music-key}") String localMusicKey) {
        if (localMusicKey == null || localMusicKey.isBlank()) {
            throw new IllegalStateException(
                    "record-card.music.local-ai.enabled=true인데 RECORD_CARD_MUSIC_LOCAL_AI_MUSIC_KEY가 비어 있습니다");
        }
        this.recordCardMusicOutboxAdapter = recordCardMusicOutboxAdapter;
        this.taskScheduler = taskScheduler;
        this.applicationEventPublisher = applicationEventPublisher;
        this.jsonMapper = jsonMapper;
        this.kafkaNamingProperties = kafkaNamingProperties;
        this.localMusicKey = localMusicKey;
    }

    @Override
    public void request(RecordCardMusicRequest request) {
        // 1. 요청은 운영과 같이 아웃박스에 쓴다 — 카드와 함께 커밋되거나 함께 롤백된다
        recordCardMusicOutboxAdapter.request(request);

        // 2. 커밋된 뒤에만 완료를 예약한다 — 롤백된 카드에는 AI 결과가 오지 않는다
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskScheduler.schedule(() -> publishGenerated(request), Instant.now().plus(DELAY));
            }
        });
    }

    // 받는 쪽 예외는 스케줄러가 삼키므로 여기서 남긴다 — 로컬 전용이라 다시 보내지 않는다
    private void publishGenerated(RecordCardMusicRequest request) {
        try {
            applicationEventPublisher.publishEvent(
                    new PayloadApplicationEvent<>(this, generatedRecord(request), RECORD_TYPE));
        } catch (RuntimeException e) {
            log.error("[기록 카드] 로컬 음악 완료 전달 실패 - recordCardId={}", request.recordCardId(), e);
        }
    }

    // AI 서버가 보내는 것과 같은 모양 — 헤더 id = inference_id, key = record_card_id, 본문 = snake_case JSON
    ConsumerRecord<String, String> generatedRecord(RecordCardMusicRequest request) {
        String inferenceId = UuidCreator.getTimeOrderedEpoch().toString();
        String recordCardId = request.recordCardId().toString();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                kafkaNamingProperties.topic(GENERATED_TOPIC), 0, 0L, recordCardId,
                jsonMapper.writeValueAsString(succeededBody(inferenceId, request)));
        record.headers().add(EVENT_ID_HEADER, inferenceId.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    // 값이 없어도 키를 빼지 않는다 — AI 서버의 성공 메시지와 같게
    private Map<String, Object> succeededBody(String inferenceId, RecordCardMusicRequest request) {
        Map<String, Object> invoke = new LinkedHashMap<>();
        invoke.put("http_status", 202);
        invoke.put("error_code", null);
        invoke.put("message", null);

        Map<String, Object> baseMusic = new LinkedHashMap<>();
        baseMusic.put("s3_key", localMusicKey);
        baseMusic.put("duration_seconds", BASE_MUSIC_SECONDS);

        List<Map<String, Object>> photoMusic = request.photos().stream().map(photo -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("record_card_image_id", photo.recordCardImageId().toString());
            map.put("s3_key", localMusicKey);
            map.put("duration_seconds", PHOTO_MUSIC_SECONDS);
            return map;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("base_music", baseMusic);
        result.put("photo_music", photoMusic);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inference_id", inferenceId);
        body.put("record_card_id", request.recordCardId().toString());
        body.put("status", "SUCCEEDED");
        body.put("failed_stage", null);
        body.put("invoke", invoke);
        body.put("result", result);
        return body;
    }
}
