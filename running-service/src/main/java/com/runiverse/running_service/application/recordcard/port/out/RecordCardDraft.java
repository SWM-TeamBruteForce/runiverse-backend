package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 작성(9-1)·수정(9-3)이 받은 내용 — 확정(9-2) 전까지 Redis에만 둔다. DB에는 사진까지 확인된 카드만 들어간다.
// 초안 ID는 요청마다 새로 나오고, 확정은 초안 ID 하나로 이 값을 읽어 종류(type)로 작성·수정을 가른다
public record RecordCardDraft(
        UUID recordCardDraftId,
        DraftType type,                 // 작성인지 수정인지 — 같은 확정(9-2)이 이 값으로 갈라진다
        UUID recordCardId,              // 이때 정한 카드 ID — 확정 때 이 값으로 저장한다
        UUID userId,                    // 초안을 만든 사람 — 다른 사람이 확정하면 404
        Long runningRecordId,
        RecordCardActivityType activityType,
        String memo,
        RecordCardVisibility visibility,
        List<DraftImage> images,        // 캐러셀 순서대로
        Integer thumbnailIndex,         // 사진이 없으면 null
        boolean confirmed,              // 확정했는지 — 확정 뒤 재시도에 같은 카드를 돌려준다
        EditPlan editPlan               // 수정(UPDATE)일 때만 — 작성이면 null
) {

    public RecordCardDraft {
        images = List.copyOf(images);
    }

    // 작성 초안 — 수정 계획이 없다
    public RecordCardDraft(UUID recordCardDraftId, DraftType type, UUID recordCardId, UUID userId,
                           Long runningRecordId, RecordCardActivityType activityType, String memo,
                           RecordCardVisibility visibility, List<DraftImage> images, Integer thumbnailIndex,
                           boolean confirmed) {
        this(recordCardDraftId, type, recordCardId, userId, runningRecordId, activityType, memo, visibility, images,
                thumbnailIndex, confirmed, null);
    }

    // 수정(9-3) 초안의 계획 — 새 사진이 있어 9-2 확정 때 반영한다. images에는 새 사진만 있다(확정이 S3에서 확인한다).
    // 바꿀 값은 null이면 그대로 둔다. slots는 수정 뒤 사진 전체를 순서대로 — 새 사진은 images의 같은 ID를 가리킨다
    public record EditPlan(
            String memo,
            RecordCardActivityType activityType,
            RecordCardVisibility visibility,
            List<Slot> slots,
            Integer thumbnailIndex,
            Long baseHistoryId          // 초안을 만들 때 카드의 마지막 수정 이력 — 없으면 null. 확정 때 달라졌으면 409
    ) {

        public EditPlan {
            slots = List.copyOf(slots);
        }

        // 수정 뒤 캐러셀의 한 자리 — 남길 사진(설명 포함)이거나, images에 있는 새 사진
        public record Slot(UUID recordCardImageId, String caption, boolean added) {

        }
    }

    // 확정한 초안 — 저장이 끝났다는 표시만 바꾼다. 재시도가 이 표시를 보고 같은 카드를 돌려받는다
    public RecordCardDraft confirm() {
        return new RecordCardDraft(recordCardDraftId, type, recordCardId, userId, runningRecordId, activityType, memo,
                visibility, images, thumbnailIndex, true, editPlan);
    }

    // 사진 하나를 바꾼 초안 — 나눠 올린 사진을 합친 뒤 그 사진만 "합침"으로 바꿀 때 쓴다
    public RecordCardDraft withImage(int index, DraftImage image) {
        List<DraftImage> next = new ArrayList<>(images);
        next.set(index, image);
        return new RecordCardDraft(recordCardDraftId, type, recordCardId, userId, runningRecordId, activityType, memo,
                visibility, next, thumbnailIndex, confirmed, editPlan);
    }

    // 올릴 사진 하나 — 확정 때 S3 실물을 여기 적힌 형식·크기와 대조한다
    public record DraftImage(
            UUID recordCardImageId,     // 이때 정한 사진 ID — 사진 key의 사진 폴더이고, 확정 때 이 값으로 저장한다
            String imageKey,
            String contentType,         // 소문자 정규값 — 서명에 넣은 값과 같다
            long fileSizeBytes,
            String caption,             // 설명이 없으면 null
            UploadType uploadType,
            String multipartUploadId,   // MULTIPART일 때만 — S3가 준 UploadId, 앱에는 주지 않는다
            Integer partCount,          // MULTIPART일 때만 — 조각 크기 설정이 바뀌어도 발급한 개수로 확인한다
            boolean merged,             // 조각을 합쳤는지 — 다른 사진 때문에 확정이 실패해 다시 부를 때 다시 합치지 않는다
            long thumbnailFileSizeBytes // 작은 버전(썸네일) 크기 — 확정 때 실물과 대조한다. key는 원본 key에서 계산한다
                                        // (RecordCardStorageKeys.thumbnailKeyOf), 형식은 원본과 같다.
                                        // 이 값이 없던 초안은 읽기부터 실패한다(Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES, 확정이 500) —
                                        // 초안은 30분이면 사라져 옮기지 않는다
    ) {

        // 조각을 합쳤다 — 다른 사진 때문에 확정이 실패해 다시 부를 때 다시 합치지 않는다
        public DraftImage merge() {
            return new DraftImage(recordCardImageId, imageKey, contentType, fileSizeBytes, caption, uploadType,
                    multipartUploadId, partCount, true, thumbnailFileSizeBytes);
        }
    }

    // 작성(9-1)인지 수정(9-3, 새 사진이 있을 때)인지 — 확정(9-2)이 이 값으로 저장을 가른다
    public enum DraftType {
        CREATE,
        UPDATE
    }

    public enum UploadType {
        SINGLE,
        MULTIPART
    }
}
