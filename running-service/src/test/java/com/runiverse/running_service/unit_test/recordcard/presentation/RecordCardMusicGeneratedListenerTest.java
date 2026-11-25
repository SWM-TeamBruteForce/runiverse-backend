package com.runiverse.running_service.unit_test.recordcard.presentation;

import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand.PhotoMusic;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardMusicUsecase;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.presentation.recordcard.kafka.RecordCardMusicGeneratedListener;
import com.runiverse.running_service.presentation.recordcard.kafka.RecordCardMusicGeneratedMessageMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 음악 완료 이벤트 리스너 단위 테스트")
class RecordCardMusicGeneratedListenerTest {

    private static final String TOPIC = "test.record-card.music.generated";
    private static final String EVENT_ID = "1c298034-bf6f-4f06-a983-dc3b59822e74";
    private static final String CARD_ID = "01a1ee0f-4fee-7741-a7b1-7eeb86bb369f";
    private static final String IMAGE_A = "01a1ee0f-4fee-7d58-a7b2-925ef4d37f36";
    private static final String IMAGE_B = "01a1ee0f-5029-7488-9b2d-c7b5d4580997";

    private static final String SUCCEEDED = """
            {
              "inference_id": "1c298034-bf6f-4f06-a983-dc3b59822e74",
              "record_card_id": "01a1ee0f-4fee-7741-a7b1-7eeb86bb369f",
              "status": "SUCCEEDED",
              "failed_stage": null,
              "invoke": { "http_status": 202, "error_code": null, "message": null },
              "result": {
                "base_music": { "s3_key": "audio/test/u/DAILY/c/base.wav", "duration_seconds": 30.0 },
                "photo_music": [
                  { "record_card_image_id": "01a1ee0f-4fee-7d58-a7b2-925ef4d37f36",
                    "s3_key": "audio/test/u/DAILY/c/a.wav", "duration_seconds": 10.0 },
                  { "record_card_image_id": "01a1ee0f-5029-7488-9b2d-c7b5d4580997",
                    "s3_key": "audio/test/u/DAILY/c/b.wav", "duration_seconds": 10.0 }
                ]
              }
            }
            """;

    private static final String FAILED = """
            {
              "inference_id": "1c298034-bf6f-4f06-a983-dc3b59822e74",
              "record_card_id": "01a1ee0f-4fee-7741-a7b1-7eeb86bb369f",
              "status": "FAILED",
              "failed_stage": "INFERENCE",
              "invoke": { "http_status": 202, "error_code": null, "message": null },
              "result": {
                "base_music": { "s3_key": null, "duration_seconds": null },
                "photo_music": [
                  { "record_card_image_id": "01a1ee0f-4fee-7d58-a7b2-925ef4d37f36",
                    "s3_key": null, "duration_seconds": null }
                ],
                "error": { "code": "MUSIC_GENERATION_OOM", "message": "사진 0(CudaOutOfMemory)" }
              }
            }
            """;

    @Mock
    private CompleteRecordCardMusicUsecase usecase;

    private RecordCardMusicGeneratedListener listener;

    @BeforeEach
    void setUp() {
        // 실제 Mapper로 조립한다 — 검사·변환을 옮기기 전과 같은 케이스가 그대로 통과해야 한다
        listener = new RecordCardMusicGeneratedListener(
                new RecordCardMusicGeneratedMessageMapper(JsonMapper.builder().build()), usecase);
    }

    @Test
    @DisplayName("성공 결과는 COMPLETED와 받은 key로 옮겨 헤더 id·토픽·원문과 함께 넘긴다")
    void passesSucceeded() {
        // when
        listener.onMessage(record(CARD_ID, SUCCEEDED, EVENT_ID));

        // then
        CompleteRecordCardMusicCommand command = captured();
        assertThat(command.topic()).isEqualTo(TOPIC);
        assertThat(command.eventId()).isEqualTo(EVENT_ID);
        assertThat(command.recordCardId()).isEqualTo(UUID.fromString(CARD_ID));
        assertThat(command.result()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(command.baseMusicKey()).isEqualTo("audio/test/u/DAILY/c/base.wav");
        assertThat(command.photoMusics()).containsExactly(
                new PhotoMusic(UUID.fromString(IMAGE_A), "audio/test/u/DAILY/c/a.wav"),
                new PhotoMusic(UUID.fromString(IMAGE_B), "audio/test/u/DAILY/c/b.wav"));
        assertThat(command.failedStage()).isNull();
        assertThat(command.failureCode()).isNull();
        assertThat(command.payload()).isEqualTo(SUCCEEDED);
    }

    @Test
    @DisplayName("실패 결과는 FAILED와 key 없이 옮기고 원인 코드는 result.error.code다")
    void passesFailed() {
        // when
        listener.onMessage(record(CARD_ID, FAILED, EVENT_ID));

        // then
        CompleteRecordCardMusicCommand command = captured();
        assertThat(command.result()).isEqualTo(MusicStatus.FAILED);
        assertThat(command.baseMusicKey()).isNull();
        assertThat(command.photoMusics()).containsExactly(new PhotoMusic(UUID.fromString(IMAGE_A), null));
        assertThat(command.failedStage()).isEqualTo("INFERENCE");
        assertThat(command.failureCode()).isEqualTo("MUSIC_GENERATION_OOM");
    }

    @Test
    @DisplayName("호출 단계 실패는 원인 코드를 invoke.error_code에서 가져온다")
    void failureCodeFromInvoke() {
        // given
        String invokeFailed = FAILED
                .replace("\"failed_stage\": \"INFERENCE\"", "\"failed_stage\": \"INVOKE\"")
                .replace("\"http_status\": 202, \"error_code\": null", "\"http_status\": 400, \"error_code\": \"ValidationError\"")
                .replace(", \"error\": { \"code\": \"MUSIC_GENERATION_OOM\", \"message\": \"사진 0(CudaOutOfMemory)\" }", "")
                .replace("],\n    \"error\": { \"code\": \"MUSIC_GENERATION_OOM\", \"message\": \"사진 0(CudaOutOfMemory)\" }", "]");

        // when
        listener.onMessage(record(CARD_ID, invokeFailed, EVENT_ID));

        // then
        assertThat(captured().failureCode()).isEqualTo("ValidationError");
    }

    @Test
    @DisplayName("헤더 id가 없으면 받지 않는다")
    void skipsWithoutEventId() {
        // when
        listener.onMessage(record(CARD_ID, SUCCEEDED, null));

        // then
        verifyNoInteractions(usecase);
    }

    @Test
    @DisplayName("헤더 id가 inbox 키 길이(64)를 넘으면 받지 않는다")
    void skipsTooLongEventId() {
        // when
        listener.onMessage(record(CARD_ID, SUCCEEDED, "x".repeat(65)));

        // then
        verifyNoInteractions(usecase);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "{\"record_card_id\": \"not-a-uuid\", \"status\": \"SUCCEEDED\"}"
    })
    @DisplayName("본문을 읽지 못하면 건너뛴다")
    void skipsUnreadableBody(String body) {
        // when
        listener.onMessage(record(CARD_ID, body, EVENT_ID));

        // then
        verifyNoInteractions(usecase);
    }

    @Test
    @DisplayName("본문의 카드 ID가 Kafka key와 다르면 건너뛴다")
    void skipsKeyMismatch() {
        // when
        listener.onMessage(record("01a1ee0f-0000-7000-8000-000000000000", SUCCEEDED, EVENT_ID));

        // then
        verifyNoInteractions(usecase);
    }

    @Test
    @DisplayName("모르는 status면 건너뛴다")
    void skipsUnknownStatus() {
        // when
        listener.onMessage(record(CARD_ID, SUCCEEDED.replace("\"SUCCEEDED\"", "\"PARTIAL\""), EVENT_ID));

        // then
        verifyNoInteractions(usecase);
    }

    @Test
    @DisplayName("음악 목록이 없으면 어느 음악인지 몰라 건너뛴다")
    void skipsWithoutMusicList() {
        // given
        String noList = """
                {"inference_id": "x", "record_card_id": "01a1ee0f-4fee-7741-a7b1-7eeb86bb369f",
                 "status": "FAILED", "failed_stage": "INVOKE", "result": null}
                """;

        // when
        listener.onMessage(record(CARD_ID, noList, EVENT_ID));

        // then
        verifyNoInteractions(usecase);
    }

    @Test
    @DisplayName("성공인데 음악 key가 하나라도 없으면 건너뛴다")
    void skipsSucceededWithoutKey() {
        // when
        listener.onMessage(record(CARD_ID,
                SUCCEEDED.replace("\"audio/test/u/DAILY/c/b.wav\"", "null"), EVENT_ID));

        // then
        verifyNoInteractions(usecase);
    }

    @Test
    @DisplayName("usecase의 예외는 그대로 올린다 — 오프셋을 커밋하지 않아 다시 받는다")
    void propagatesUsecaseFailure() {
        // given
        willThrow(new IllegalStateException("db down")).given(usecase).handle(any());

        // when & then
        assertThatThrownBy(() -> listener.onMessage(record(CARD_ID, SUCCEEDED, EVENT_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    private CompleteRecordCardMusicCommand captured() {
        ArgumentCaptor<CompleteRecordCardMusicCommand> captor =
                ArgumentCaptor.forClass(CompleteRecordCardMusicCommand.class);
        verify(usecase).handle(captor.capture());
        return captor.getValue();
    }

    private static ConsumerRecord<String, String> record(String key, String value, String eventId) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 7L, key, value);
        if (eventId != null) {
            record.headers().add("id", eventId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
