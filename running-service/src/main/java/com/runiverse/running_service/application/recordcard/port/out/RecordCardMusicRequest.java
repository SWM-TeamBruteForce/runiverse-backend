package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// AI 서버에 보낼 음악 생성 요청 — 보내는 형식(필드 이름·시간대)은 어댑터가 정한다.
// 작성 확정(9-2)은 기본곡 + 카드 사진 전부를 요청한다
public record RecordCardMusicRequest(
        UUID userId,
        UUID recordCardId,
        String memo,
        RecordCardActivityType activityType,
        RunningSummary runningSummary,
        List<Photo> photos              // 카드 순서(sortOrder)대로 — 사진이 없으면 빈 목록
) {

    public RecordCardMusicRequest {
        photos = List.copyOf(photos);
    }

    // 카드가 붙은 러닝 기록의 수치 — 발행할 때 running_records에서 읽는다. AI 서버는 기록을 조회하지 않는다
    public record RunningSummary(
            int totalDistance,              // 미터
            int totalDuration,              // 초 — 일시정지·정지 구간은 빠진다
            int avgPace,                    // 초/km
            Integer avgCadence,             // spm — 기록에 없으면 null
            LocalDateTime startAt,          // 앱 시간대(KST) 값 그대로 — 오프셋은 어댑터가 붙인다
            int weatherCode,                // WMO 4677
            BigDecimal temperature,         // 섭씨
            Integer totalElevationGain      // 미터 — 유효 표본이 부족하면 null
    ) {

    }

    public record Photo(UUID recordCardImageId, String imageKey, String caption) {

    }

    // 방금 만든 카드로 요청을 만든다 — 사진은 카드가 이미 sortOrder 순으로 들고 있다
    public static RecordCardMusicRequest of(RecordCard recordCard, RunningSummary runningSummary) {
        return new RecordCardMusicRequest(
                recordCard.getUserId().value(),
                recordCard.getRecordCardId().value(),
                recordCard.getMemo().value(),
                recordCard.getActivityType(),
                runningSummary,
                recordCard.getImages().stream().map(RecordCardMusicRequest::photoOf).toList());
    }

    private static Photo photoOf(RecordCardImage image) {
        return new Photo(image.getRecordCardImageId().value(), image.getImageKey().value(),
                image.getCaption().map(RecordCardImageCaption::value).orElse(null));
    }
}
