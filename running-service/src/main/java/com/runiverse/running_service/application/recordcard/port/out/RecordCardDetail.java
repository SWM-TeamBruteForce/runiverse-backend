package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 상세(8-1)가 읽는 카드 한 장 — 조회 전용 모양이다(도메인 RecordCard는 쓰기용이라 기록 수치·경로를 갖지 않는다).
// key는 저장된 값 그대로다 — URL로 바꾸는 것은 유스케이스다
public record RecordCardDetail(
        UUID recordCardId,
        UUID userId,
        RecordCardVisibility visibility,
        RecordCardActivityType activityType,
        String memo,
        String thumbnailImageKey,       // 썸네일 사진의 key — 사진이 없으면 null
        MusicStatus baseMusicStatus,
        String baseMusicKey,            // COMPLETED일 때만 있다
        List<Image> images,             // sort_order 순서
        Record record,
        List<UUID> companionUserIds,    // 같이 뛴 사람 — 그 러닝을 함께 출발한 참가자 중 작성자를 뺀 사람(6-1 players와 같은 기준)
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public RecordCardDetail {
        images = List.copyOf(images);
        companionUserIds = List.copyOf(companionUserIds);
    }

    public record Image(
            UUID recordCardImageId,
            String imageKey,
            String caption,             // 없으면 null
            MusicStatus musicStatus,
            String musicKey             // COMPLETED일 때만 있다
    ) {

    }

    // 카드가 붙은 러닝 기록 — 수치는 카드가 복사해 두지 않고 running_records에서 읽는다
    public record Record(
            Long runningRecordId,
            LocalDateTime startedAt,
            int totalDistanceMeters,
            int totalDurationSeconds,
            int averagePaceSecondsPerKm,
            List<String> segmentPolylines   // segment_number 순서 — 구간 기록이 없으면 빈 목록
    ) {

        public Record {
            segmentPolylines = List.copyOf(segmentPolylines);
        }
    }
}
