package com.runiverse.running_service.unit_test.infrastructure.kafka;

import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand.PhotoMusic;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties.Env;
import com.runiverse.running_service.infrastructure.kafka.local.LocalAiMusicAdapter;
import com.runiverse.running_service.infrastructure.kafka.outbox.RecordCardMusicOutboxAdapter;
import com.runiverse.running_service.presentation.recordcard.kafka.RecordCardMusicGeneratedMessageMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("로컬 가짜 AI 음악 어댑터 단위 테스트")
class LocalAiMusicAdapterTest {

    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440000");
    private static final UUID IMAGE_A = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final UUID IMAGE_B = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012e");
    private static final Duration DELAY = Duration.ofSeconds(5);
    private static final String MUSIC_KEY = "Night_Walk.wav";

    @Mock
    private RecordCardMusicOutboxAdapter outboxAdapter;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    // 운영 Kafka 리스너와 같은 Mapper — 가짜 메시지가 실제 검사를 통과하는지 본다
    private final RecordCardMusicGeneratedMessageMapper mapper = new RecordCardMusicGeneratedMessageMapper(jsonMapper);

    private LocalAiMusicAdapter adapter;

    @BeforeEach
    void setUp() {
        // 카드 저장 트랜잭션 안에서 불린다 — 커밋 콜백을 등록할 수 있게 동기화를 연다
        TransactionSynchronizationManager.initSynchronization();
        adapter = new LocalAiMusicAdapter(outboxAdapter, taskScheduler, eventPublisher, jsonMapper,
                new KafkaNamingProperties(Env.DEV), MUSIC_KEY);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("요청은 진짜 아웃박스 어댑터에 그대로 넘기고, 커밋 전에는 완료를 예약하지 않는다")
    void delegatesToOutboxAndWaitsForCommit() {
        // given
        RecordCardMusicRequest request = request(List.of());

        // when
        adapter.request(request);

        // then
        verify(outboxAdapter).request(request);
        verifyNoInteractions(taskScheduler, eventPublisher);
    }

    @Test
    @DisplayName("롤백되면 완료를 예약하지 않는다")
    void skipsOnRollback() {
        // given
        adapter.request(request(List.of()));

        // when
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // then
        verifyNoInteractions(taskScheduler, eventPublisher);
    }

    @Test
    @DisplayName("커밋 뒤 5초 뒤로 완료를 예약한다")
    void schedulesAfterCommit() {
        // given
        adapter.request(request(List.of()));
        Instant before = Instant.now();

        // when
        commit();

        // then
        Instant after = Instant.now();
        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), at.capture());
        assertThat(at.getValue()).isBetween(before.plus(DELAY), after.plus(DELAY));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("AI 서버와 같은 모양으로 보낸다 — dev 완료 토픽·카드 ID key·UUIDv7 헤더 id")
    void publishesKafkaShapedRecord() {
        // when
        ConsumerRecord<String, String> record = fire(request(List.of()));

        // then
        assertThat(record.topic()).isEqualTo("dev.record-card.music.generated");
        assertThat(record.key()).isEqualTo(CARD_ID.toString());
        String eventId = new String(record.headers().lastHeader("id").value(), StandardCharsets.UTF_8);
        assertThat(UUID.fromString(eventId).version()).isEqualTo(7);
        assertThat(jsonMapper.readTree(record.value()).get("inference_id").asString()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("보낸 메시지는 운영 Mapper를 통과해 성공 Command가 된다 — 기본곡·사진곡 모두 설정한 key")
    void passesProductionMapper() {
        // given
        RecordCardMusicRequest request = request(List.of(
                new Photo(IMAGE_A, "record-cards/a.jpg", "노을"),
                new Photo(IMAGE_B, "record-cards/b.jpg", null)));

        // when
        ConsumerRecord<String, String> record = fire(request);

        // then
        CompleteRecordCardMusicCommand command = mapper.toCommand(record).orElseThrow();
        assertThat(command.topic()).isEqualTo("dev.record-card.music.generated");
        assertThat(command.recordCardId()).isEqualTo(CARD_ID);
        assertThat(command.result()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(command.baseMusicKey()).isEqualTo(MUSIC_KEY);
        assertThat(command.photoMusics()).containsExactly(
                new PhotoMusic(IMAGE_A, MUSIC_KEY),
                new PhotoMusic(IMAGE_B, MUSIC_KEY));
        assertThat(command.failedStage()).isNull();
        assertThat(command.failureCode()).isNull();
        assertThat(command.payload()).isEqualTo(record.value());
    }

    @Test
    @DisplayName("사진이 없으면 기본곡만 담긴다")
    void withoutPhotos() {
        // when
        ConsumerRecord<String, String> record = fire(request(List.of()));

        // then
        CompleteRecordCardMusicCommand command = mapper.toCommand(record).orElseThrow();
        assertThat(command.baseMusicKey()).isEqualTo(MUSIC_KEY);
        assertThat(command.photoMusics()).isEmpty();
    }

    @Test
    @DisplayName("받는 쪽이 실패해도 예약 작업은 예외를 밖으로 내지 않는다 — 로그로 남긴다")
    void swallowsReceiverFailureWithLog() {
        // given
        willThrow(new IllegalStateException("db down")).given(eventPublisher).publishEvent(any(ApplicationEvent.class));
        adapter.request(request(List.of()));
        commit();

        // when & then
        assertThatCode(() -> scheduledTask().run()).doesNotThrowAnyException();
        verify(eventPublisher).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    @DisplayName("이벤트 타입을 ConsumerRecord<String, String>으로 밝혀 보낸다 — 지워진 제네릭으로는 수신기가 받지 않는다")
    void publishesWithGenericType() {
        // when
        PayloadApplicationEvent<?> event = firedEvent(request(List.of()));

        // then
        assertThat(event.getResolvableType().getGeneric(0))
                .hasToString(ResolvableType.forClassWithGenerics(ConsumerRecord.class, String.class, String.class)
                        .toString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("음악 key가 비어 있으면 만들지 않는다 — key 없는 성공 메시지는 Mapper가 건너뛰어 조용히 GENERATING에 머문다")
    void rejectsBlankMusicKey(String musicKey) {
        assertThatThrownBy(() -> new LocalAiMusicAdapter(outboxAdapter, taskScheduler, eventPublisher, jsonMapper,
                new KafkaNamingProperties(Env.DEV), musicKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECORD_CARD_MUSIC_LOCAL_AI_MUSIC_KEY");
    }

    // 커밋 → 예약된 작업 실행 → 발행된 이벤트를 꺼낸다
    private PayloadApplicationEvent<?> firedEvent(RecordCardMusicRequest request) {
        adapter.request(request);
        commit();
        scheduledTask().run();
        ArgumentCaptor<ApplicationEvent> event = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        return (PayloadApplicationEvent<?>) event.getValue();
    }

    @SuppressWarnings("unchecked")
    private ConsumerRecord<String, String> fire(RecordCardMusicRequest request) {
        return (ConsumerRecord<String, String>) firedEvent(request).getPayload();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(task.capture(), any(Instant.class));
        return task.getValue();
    }

    private static RecordCardMusicRequest request(List<Photo> photos) {
        return new RecordCardMusicRequest(USER_ID, CARD_ID, "한강 한 바퀴", RecordCardActivityType.DAILY,
                new RunningSummary(5020, 1800, 359, null, LocalDateTime.of(2026, 7, 25, 19, 0, 30), 0,
                        new BigDecimal("24.5"), null),
                photos);
    }
}
