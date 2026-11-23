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
// 작성 확정(9-2)은 기본곡 + 카드 사진 전부, 수정(9-3)·다시 만들기(9-6)는 다시 만들 사진만 요청한다
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

    // 수정(9-3)이 보내는 요청 — 다시 만들 음악은 도메인(RecordCard.Edit)이 정한 것뿐이다. 카드의 사진 전체를 받는 경로가 없다.
    // 재료가 바뀌지 않았으면 만들 수 없다 — AI 서버가 요청마다 기본곡을 다시 만들어, 잘못 보내면 잘 만든 곡이 바뀐다
    public static RecordCardMusicRequest forEdit(RecordCard recordCard, RecordCard.Edit edit,
                                                 RunningSummary runningSummary) {
        if (!edit.needsMusic()) {
            throw new IllegalArgumentException("음악 재료가 바뀌지 않은 수정으로는 음악을 요청할 수 없다");
        }
        // 보낼 사진은 지금 카드에 있고 생성 중이어야 한다 — 뺀 사진·끝난 음악이 섞이면 잘못 고른 것이다
        for (RecordCardImage image : edit.imagesToRegenerate()) {
            boolean inCard = recordCard.getImages().stream()
                    .anyMatch(cardImage -> cardImage.getRecordCardImageId().equals(image.getRecordCardImageId()));
            if (!inCard || !image.getMusicStatus().isGenerating()) {
                throw new IllegalArgumentException("다시 만들 사진은 카드에 있고 생성 중이어야 한다");
            }
        }
        return new RecordCardMusicRequest(
                recordCard.getUserId().value(),
                recordCard.getRecordCardId().value(),
                recordCard.getMemo().value(),
                recordCard.getActivityType(),
                runningSummary,
                edit.imagesToRegenerate().stream().map(RecordCardMusicRequest::photoOf).toList());
    }

    // 다시 만들기(9-6)가 보내는 요청 — 생성 중인 사진(RecordCard.retryMusic이 되돌린 실패한 사진)만 싣는다.
    // 완료된 사진은 싣지 않는다 — 다시 만들면 잘 만든 곡이 바뀐다. 기본곡은 싣지 않아도 AI 서버가 요청마다 만든다.
    // 되돌린 음악이 하나도 없으면(기본곡도 사진도 생성 중이 아니면) 순서가 틀린 것이다
    public static RecordCardMusicRequest forRetry(RecordCard recordCard, RunningSummary runningSummary) {
        List<Photo> photos = recordCard.getImages().stream()
                .filter(image -> image.getMusicStatus().isGenerating())
                .map(RecordCardMusicRequest::photoOf)
                .toList();
        if (!recordCard.getBaseMusicStatus().isGenerating() && photos.isEmpty()) {
            throw new IllegalArgumentException("다시 만들 음악이 없는데 다시 만들기 요청을 만들 수 없다");
        }
        return new RecordCardMusicRequest(
                recordCard.getUserId().value(),
                recordCard.getRecordCardId().value(),
                recordCard.getMemo().value(),
                recordCard.getActivityType(),
                runningSummary,
                photos);
    }

    private static Photo photoOf(RecordCardImage image) {
        return new Photo(image.getRecordCardImageId().value(), image.getImageKey().value(),
                image.getCaption().map(RecordCardImageCaption::value).orElse(null));
    }
}
