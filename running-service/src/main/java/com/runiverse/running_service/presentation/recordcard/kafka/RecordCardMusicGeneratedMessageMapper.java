package com.runiverse.running_service.presentation.recordcard.kafka;

import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand.PhotoMusic;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.presentation.recordcard.kafka.RecordCardMusicGeneratedMessage.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

// 음악 생성 완료 메시지를 검사해 Command로 옮긴다 — Kafka 리스너(RecordCardMusicGeneratedListener)와
// 로컬 가짜 수신기(LocalRecordCardMusicGeneratedListener)가 같이 쓴다(받는 길만 다르고 같은 검사를 탄다).
// 계약 위반(헤더 id 없음·본문 형식·필수 값)은 ERROR를 남기고 빈 값을 돌려준다 — 다시 받아도 계속 실패해 파티션이 멈추기 때문이다
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordCardMusicGeneratedMessageMapper {

    private static final String EVENT_ID_HEADER = "id";
    private static final int MAX_EVENT_ID_LENGTH = 64;   // inbox_events.event_id
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    private final JsonMapper jsonMapper;

    public Optional<CompleteRecordCardMusicCommand> toCommand(ConsumerRecord<String, String> record) {
        // 1. 이벤트 키 — 헤더 id가 없으면 중복을 거를 수 없어 받지 않는다
        //    Spring 메시지 헤더의 id(자동 생성 UUID)와 겹치지 않게 Kafka 헤더를 직접 읽는다
        Header header = record.headers().lastHeader(EVENT_ID_HEADER);
        if (header == null || header.value() == null || header.value().length == 0) {
            return skip("헤더 id 없음", record);
        }
        String eventId = new String(header.value(), StandardCharsets.UTF_8);
        if (eventId.isBlank() || eventId.length() > MAX_EVENT_ID_LENGTH) {
            return skip("헤더 id 형식 불일치", record);
        }

        // 2. 본문
        if (record.value() == null) {
            return skip("본문 없음", record);
        }
        RecordCardMusicGeneratedMessage message;
        try {
            message = jsonMapper.readValue(record.value(), RecordCardMusicGeneratedMessage.class);
        } catch (JacksonException e) {
            log.error("[기록 카드] 음악 완료 수신 건너뜀: 본문 형식 불일치 - topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), e);
            return Optional.empty();
        }

        // 3. 대상 — 본문의 카드와 Kafka key가 같아야 한다(같은 카드는 같은 파티션)
        if (message.recordCardId() == null || !message.recordCardId().toString().equals(record.key())) {
            return skip("카드 ID 없음 또는 key 불일치", record);
        }

        // 4. 결과 — 요청 단위로 성공·실패 둘뿐이다
        MusicStatus result;
        if (SUCCEEDED.equals(message.status())) {
            result = MusicStatus.COMPLETED;
        } else if (FAILED.equals(message.status())) {
            result = MusicStatus.FAILED;
        } else {
            return skip("모르는 status", record);
        }

        // 5. 음악 목록 — 성공·실패 모두 요청한 음악이 담긴다. 없으면 어느 음악인지 몰라 아무것도 바꾸지 않는다
        Result body = message.result();
        if (body == null || body.baseMusic() == null || body.photoMusic() == null
                || body.photoMusic().stream().anyMatch(photo -> photo == null || photo.recordCardImageId() == null)) {
            return skip("음악 목록 없음", record);
        }

        // 6. 성공이면 key가 모두 있어야 한다 — 없는 채로 넘기면 도메인이 거부해 계속 다시 받는다
        boolean completed = result == MusicStatus.COMPLETED;
        if (completed && (isBlank(body.baseMusic().s3Key())
                || body.photoMusic().stream().anyMatch(photo -> isBlank(photo.s3Key())))) {
            return skip("성공인데 음악 key 없음", record);
        }

        // 실패면 key를 버린다 — 만들지 않은 음악이다
        List<PhotoMusic> photoMusics = body.photoMusic().stream()
                .map(photo -> new PhotoMusic(photo.recordCardImageId(), completed ? photo.s3Key() : null))
                .toList();
        return Optional.of(new CompleteRecordCardMusicCommand(
                record.topic(), eventId, message.recordCardId(), result,
                completed ? body.baseMusic().s3Key() : null, photoMusics,
                message.failedStage(), failureCode(message), record.value()));
    }

    // 실패 원인 코드 — 추론 중 실패는 result.error.code, 호출 실패(INVOKE)는 invoke.error_code에 온다
    private static String failureCode(RecordCardMusicGeneratedMessage message) {
        String fromResult = message.result().error() == null ? null : message.result().error().code();
        String fromInvoke = message.invoke() == null ? null : message.invoke().errorCode();
        return fromResult != null ? fromResult : fromInvoke;
    }

    // 계약 위반 — 처리하지 않고 넘어간다(오프셋은 커밋된다). 본문은 남기지 않고 위치만 남긴다(원문은 토픽에서 찾는다)
    private static Optional<CompleteRecordCardMusicCommand> skip(String cause, ConsumerRecord<String, String> record) {
        log.error("[기록 카드] 음악 완료 수신 건너뜀: {} - topic={}, partition={}, offset={}, key={}",
                cause, record.topic(), record.partition(), record.offset(), record.key());
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
