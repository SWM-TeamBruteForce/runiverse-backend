package com.runiverse.running_service.presentation.recordcard.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

// 음악 생성 완료 이벤트({env}.record-card.music.generated)의 본문 — AI 서버와 맞춘 snake_case 계약이라 이름을 바꾸지 않는다.
// 쓰는 필드만 읽는다(duration_seconds·message 원문은 읽지 않는다). 형식이 맞는지는 리스너가 본다
public record RecordCardMusicGeneratedMessage(
        @JsonProperty("inference_id") String inferenceId,
        @JsonProperty("record_card_id") UUID recordCardId,
        @JsonProperty("status") String status,              // SUCCEEDED · FAILED
        @JsonProperty("failed_stage") String failedStage,   // 실패 단계 — 성공이면 null
        @JsonProperty("invoke") Invoke invoke,
        @JsonProperty("result") Result result
) {

    // AI 서버의 호출 결과 — INVOKE 단계 실패의 원인 코드가 여기 온다. 원인 문장(message)은 읽지 않는다(계정 정보가 섞여 온 적이 있다)
    public record Invoke(
            @JsonProperty("http_status") Integer httpStatus,
            @JsonProperty("error_code") String errorCode
    ) {

    }

    public record Result(
            @JsonProperty("base_music") Music baseMusic,
            @JsonProperty("photo_music") List<PhotoMusic> photoMusic,  // 요청한 사진마다 하나 — 실패여도 담긴다
            @JsonProperty("error") Error error                       // 실패일 때만
    ) {

    }

    public record Music(@JsonProperty("s3_key") String s3Key) {

    }

    public record PhotoMusic(
            @JsonProperty("record_card_image_id") UUID recordCardImageId,
            @JsonProperty("s3_key") String s3Key                     // 실패면 null
    ) {

    }

    // 원인 문장(message)은 읽지 않는다 — 로그에 남기지 않기로 한 값이다. 원문은 inbox payload에 남는다
    public record Error(@JsonProperty("code") String code) {

    }
}
