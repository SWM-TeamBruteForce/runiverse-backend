package com.runiverse.running_service.application.recordcard.command.completedraft;

import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor.Changes;
import com.runiverse.running_service.application.recordcard.exception.RecordCardChangedException;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.EditPlan;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageDraft;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 9-2 수정 확정의 DB 반영 — 이 메서드만 트랜잭션이다. S3 확인은 확정 핸들러가 먼저 끝냈다(작성 확정과 같은 흐름).
// 작성 저장(RecordCardDraftSaver)과 나눴다 — 작성은 카드를 새로 만들고, 수정은 있는 카드를 고친다(잠금·409·이력·음악 범위가 다르다)
@Component
@RequiredArgsConstructor
public class RecordCardEditDraftSaver {

    private final RecordCardEditor recordCardEditor;

    @Transactional
    public void save(RecordCardDraft draft) {
        EditPlan plan = Objects.requireNonNull(draft.editPlan(), "수정 초안에는 수정 계획이 있어야 한다");
        List<ImageSlot> slots = toSlots(plan, draft.images());

        // 1. 내 카드면 기록 행을 잠그고 읽는다 — 지웠거나 남의 카드면 잠그지 않고 404
        RecordCard card = recordCardEditor.lockOwnCard(draft.userId(), draft.recordCardId(), null);

        // 2. 이미 이 초안을 반영했으면 끝난 것이다 — 커밋 뒤 확정 표시 전에 끊긴 재시도. 새 사진 ID는 초안마다 새로 정해 겹치지 않는다
        if (alreadyApplied(card, draft.images())) {
            return;
        }

        // 3. 초안을 만든 뒤 다른 수정이 끼어들었으면 409 — 옛 모습을 기준으로 만든 초안이다(음악 완료는 이력을 남기지 않아 걸리지 않는다)
        if (!Objects.equals(recordCardEditor.lastHistoryId(card).orElse(null), plan.baseHistoryId())) {
            throw new RecordCardChangedException();
        }

        // 4. 남길 사진이 이 카드 것인지(400) — 다른 수정이 사진을 뺐다면 3에서 409로 먼저 걸린다
        recordCardEditor.requireKeptImagesInCard(card, slots);

        // 5. 반영 — 9-3 바로 반영과 같다(이력 → 저장 → 다시 만들 음악만 요청)
        recordCardEditor.apply(card, new Changes(plan.memo(), plan.activityType(), plan.visibility(), slots,
                plan.thumbnailIndex()));
    }

    // 계획의 자리를 도메인 자리로 — 새 사진은 초안의 사진(ID·key·설명)으로 넣는다
    private static List<ImageSlot> toSlots(EditPlan plan, List<DraftImage> images) {
        Map<UUID, DraftImage> newImages = images.stream()
                .collect(Collectors.toMap(DraftImage::recordCardImageId, Function.identity()));
        return plan.slots().stream()
                .map(slot -> slot.added()
                        ? (ImageSlot) new ImageSlot.Add(toDraft(newImages.get(slot.recordCardImageId())))
                        : new ImageSlot.Keep(slot.recordCardImageId(), slot.caption()))
                .toList();
    }

    private static ImageDraft toDraft(DraftImage image) {
        Objects.requireNonNull(image, "수정 계획의 새 사진이 초안 사진에 없다");
        return new ImageDraft(image.recordCardImageId(), image.imageKey(), image.caption());
    }

    private static boolean alreadyApplied(RecordCard card, List<DraftImage> newImages) {
        return newImages.stream().anyMatch(image -> card.getImages().stream()
                .map(RecordCardImage::getRecordCardImageId)
                .anyMatch(id -> id.value().equals(image.recordCardImageId())));
    }
}
