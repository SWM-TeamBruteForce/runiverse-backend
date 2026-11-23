package com.runiverse.running_service.application.recordcard.command.update;

import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardImageException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardThumbnailOutOfRangeException;
import com.runiverse.running_service.application.recordcard.port.out.LoadLastRecordCardHistoryPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRunningSummaryPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.application.recordcard.port.out.SnapshotRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 수정 한 벌을 카드에 반영한다 — 9-3 바로 반영(applyNow)과 9-2 수정 확정(자기 트랜잭션에서 lockOwnCard·apply)이 같이 쓴다.
// 내 카드 확인 → 기록 행 잠금 → 도메인에서 수정 계산 → 바뀌었으면 이력 → 저장 → 음악 재료가 바뀌었으면 요청(아웃박스).
// 다시 만들 음악은 도메인(RecordCard.Edit)이 정한다 — 여기서 사진을 고르지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordCardEditor {

    private final LoadRecordCardForUpdatePort loadRecordCardForUpdatePort;
    private final SnapshotRecordCardPort snapshotRecordCardPort;
    private final UpdateRecordCardPort updateRecordCardPort;
    private final LoadRunningSummaryPort loadRunningSummaryPort;
    private final RequestRecordCardMusicPort requestRecordCardMusicPort;
    private final LoadLastRecordCardHistoryPort loadLastRecordCardHistoryPort;

    // 바꿀 값 — null이면 그대로 둔다. slots가 null이면 사진·설명은 그대로다(썸네일 위치만 바꿀 수 있다)
    public record Changes(
            String memo,
            RecordCardActivityType activityType,
            RecordCardVisibility visibility,
            List<ImageSlot> slots,
            Integer thumbnailIndex
    ) {

    }

    // 9-3 바로 반영(새 사진 없음) — 한 트랜잭션에서 잠그고 반영한다. 9-2 수정 확정은 자기 트랜잭션에서 아래 두 메서드를 부른다
    @Transactional
    public void applyNow(UUID userId, UUID recordCardId, Changes changes) {
        RecordCard card = lockOwnCard(userId, recordCardId, changes.slots());
        apply(card, changes);
    }

    // 9-3 새 사진이 있을 때 초안을 만들기 전 — 내 카드인지(404)·남길 사진이 이 카드 것인지(400)를 먼저 보고,
    // 지금의 마지막 수정 이력을 돌려준다(초안에 적어 두고 확정 때 비교한다). 카드는 바꾸지 않는다
    @Transactional
    public Optional<Long> checkBeforeDraft(UUID userId, UUID recordCardId, List<ImageSlot> slots) {
        RecordCard card = lockOwnCard(userId, recordCardId, slots);
        return loadLastRecordCardHistoryPort.loadLastId(card.getRecordCardId());
    }

    // 지금의 마지막 수정 이력 — 9-2 수정 확정이 초안의 값과 비교한다
    public Optional<Long> lastHistoryId(RecordCard card) {
        return loadLastRecordCardHistoryPort.loadLastId(card.getRecordCardId());
    }

    // 내 카드면 기록 행을 잠그고 읽는다 — 없거나(지운 카드 포함) 남의 카드면 잠그지 않고 404.
    // 남긴다는 사진이 이 카드 것인지도 본다(400)
    public RecordCard lockOwnCard(UUID userId, UUID recordCardId, List<ImageSlot> slots) {
        RecordCard card = loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(recordCardId),
                        new UserId(userId))
                .orElseThrow(RecordCardNotFoundException::new);
        requireKeptImagesInCard(card, slots);
        return card;
    }

    // 잠그고 읽은 카드에 수정을 반영한다 — 바뀐 것이 없으면 이력·저장·요청 모두 하지 않는다
    public void apply(RecordCard card, Changes changes) {
        requireThumbnailInRange(card, changes);
        RecordCard.Edit edit = card.edit(changes.memo(), changes.activityType(), changes.visibility(),
                changes.slots(), changes.thumbnailIndex());
        if (!edit.changed()) {
            return;
        }

        // 바뀌기 직전 모습 — 저장보다 먼저 남겨야 직전 모습이다
        snapshotRecordCardPort.snapshot(card.getRecordCardId(), RecordCardSnapshotReason.UPDATE);
        updateRecordCardPort.update(card);

        // 음악은 재료가 바뀌었을 때만 — 요청 사진은 도메인이 정한 것뿐이다(설명이 그대로인 기존 사진은 싣지 않는다)
        if (edit.needsMusic()) {
            RecordCardMusicRequest.RunningSummary summary = loadRunningSummaryPort.load(card.getRunningRecordId())
                    .orElseThrow(() -> new IllegalStateException("카드가 붙은 기록의 수치가 없습니다"));
            requestRecordCardMusicPort.request(RecordCardMusicRequest.forEdit(card, edit, summary));
        }
        log.info("[기록 카드] 카드 수정 성공 - recordCardId={}, musicRequested={}, regeneratedImageCount={}, "
                        + "baseMusicIngredientChanged={}",
                card.getRecordCardId().value(), edit.needsMusic(), edit.imagesToRegenerate().size(),
                edit.baseMusicIngredientChanged());
    }

    // 사진 목록 없이 썸네일 위치만 보냈으면 지금 사진 기준이다 — 벗어나면 400(도메인에 넘기면 500).
    // 사진 목록을 보냈으면 그 목록 기준이라 Request DTO가 이미 봤다
    private static void requireThumbnailInRange(RecordCard card, Changes changes) {
        Integer index = changes.thumbnailIndex();
        if (changes.slots() == null && index != null && (index < 0 || index >= card.getImages().size())) {
            throw new RecordCardThumbnailOutOfRangeException();
        }
    }

    // 남긴다는 사진이 이 카드에 없으면 400 — 도메인에 넘기면 도메인 예외(500)가 된다
    public void requireKeptImagesInCard(RecordCard card, List<ImageSlot> slots) {
        if (slots == null) {
            return;
        }
        Set<UUID> cardImageIds = card.getImages().stream()
                .map(RecordCardImage::getRecordCardImageId)
                .map(id -> id.value())
                .collect(Collectors.toSet());
        boolean foreign = slots.stream()
                .filter(ImageSlot.Keep.class::isInstance)
                .map(ImageSlot.Keep.class::cast)
                .anyMatch(keep -> !cardImageIds.contains(keep.recordCardImageId()));
        if (foreign) {
            throw new InvalidRecordCardImageException();
        }
    }
}
