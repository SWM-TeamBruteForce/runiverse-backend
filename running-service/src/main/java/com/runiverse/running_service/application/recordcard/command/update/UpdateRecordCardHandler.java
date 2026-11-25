package com.runiverse.running_service.application.recordcard.command.update;

import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor.Changes;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer.Issued;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer.NewImage;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardDetailUsecase;
import com.runiverse.running_service.application.recordcard.port.in.UpdateRecordCardUsecase;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardDraftIdPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftType;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.EditPlan;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 9-3 기록 카드 수정 — 트랜잭션을 걸지 않는다. 새 사진이 없으면 바로 반영하고(RecordCardEditor 트랜잭션),
// 있으면 아직 반영하지 않고 Redis 초안과 업로드 URL을 준다(9-2 확정 때 반영). 응답은 늘 지금 카드 + 초안(없으면 null)이다
@Service
@RequiredArgsConstructor
public class UpdateRecordCardHandler implements UpdateRecordCardUsecase {

    private final RecordCardDraftProperties properties;
    private final RecordCardEditor recordCardEditor;
    private final RecordCardUploadIssuer recordCardUploadIssuer;
    private final GenerateRecordCardDraftIdPort generateRecordCardDraftIdPort;
    private final SaveRecordCardDraftPort saveRecordCardDraftPort;
    private final GetRecordCardDetailUsecase getRecordCardDetailUsecase;

    @Override
    public UpdateRecordCardResult handle(UpdateRecordCardCommand command) {
        RecordCardActivityType activityType = command.activityType() == null ? null
                : RecordCardActivityType.valueOf(command.activityType());
        RecordCardVisibility visibility = command.visibility() == null ? null
                : RecordCardVisibility.valueOf(command.visibility());
        List<UpdateRecordCardCommand.Image> images = command.images();

        // 1. 새 사진이 없으면 바로 반영한다 — 응답의 카드는 수정 뒤다
        if (images == null || images.stream().noneMatch(UpdateRecordCardCommand.Image::isNew)) {
            List<ImageSlot> slots = images == null ? null : images.stream()
                    .map(image -> (ImageSlot) new ImageSlot.Keep(image.recordCardImageId(), image.caption()))
                    .toList();
            recordCardEditor.applyNow(command.userId(), command.recordCardId(),
                    new Changes(command.memo(), activityType, visibility, slots, command.thumbnailIndex()));
            return new UpdateRecordCardResult(detail(command), null);
        }

        // 2. 새 사진이 있으면 초안 — 크기(400)·내 카드(404)·남길 사진(400)을 업로드 URL을 주기 전에 본다
        List<NewImage> newImages = images.stream()
                .filter(UpdateRecordCardCommand.Image::isNew)
                .map(image -> new NewImage(image.mimeType(), image.fileSizeBytes(), image.thumbnailFileSizeBytes(),
                        image.fileName(), image.caption()))
                .toList();
        recordCardUploadIssuer.rejectTooLarge(newImages);
        List<ImageSlot> kept = images.stream()
                .filter(image -> !image.isNew())
                .map(image -> (ImageSlot) new ImageSlot.Keep(image.recordCardImageId(), image.caption()))
                .toList();
        Long baseHistoryId = recordCardEditor.checkBeforeDraft(command.userId(), command.recordCardId(), kept)
                .orElse(null);

        // 3. 새 사진에만 업로드 URL을 주고, 수정 뒤 사진 자리를 순서대로 적는다 — 새 사진은 발급한 사진 ID로 가리킨다
        List<DraftImage> draftImages = new ArrayList<>();
        List<Upload> uploads = new ArrayList<>();
        List<EditPlan.Slot> slots = new ArrayList<>();
        for (UpdateRecordCardCommand.Image image : images) {
            if (!image.isNew()) {
                slots.add(new EditPlan.Slot(image.recordCardImageId(), image.caption(), false));
                continue;
            }
            Issued issued = recordCardUploadIssuer.issue(command.userId(), command.recordCardId(),
                    new NewImage(image.mimeType(), image.fileSizeBytes(), image.thumbnailFileSizeBytes(),
                            image.fileName(), image.caption()));
            draftImages.add(issued.draftImage());
            uploads.add(issued.upload());
            slots.add(new EditPlan.Slot(issued.draftImage().recordCardImageId(), issued.draftImage().caption(), true));
        }

        // 4. 초안을 저장한다 — 확정 전까지 카드는 그대로다. 초안의 images는 새 사진만이라 9-2가 그 사진들만 S3에서 확인한다
        Duration ttl = properties.ttl();
        UUID recordCardDraftId = generateRecordCardDraftIdPort.generate();
        RecordCardDraft draft = new RecordCardDraft(recordCardDraftId, DraftType.UPDATE, command.recordCardId(),
                command.userId(), null, null, null, null, draftImages, null, false,
                new EditPlan(command.memo(), activityType, visibility, slots, command.thumbnailIndex(), baseHistoryId));
        LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);
        saveRecordCardDraftPort.save(draft, ttl);

        return new UpdateRecordCardResult(detail(command),
                new CreateRecordCardDraftResult(recordCardDraftId, expiresAt, uploads));
    }

    // 응답의 카드 — 상세(8-1)와 같은 모양·규칙(URL 서명·선 아트)이라 상세 조회를 그대로 쓴다
    private GetRecordCardDetailResult detail(UpdateRecordCardCommand command) {
        return getRecordCardDetailUsecase.handle(new GetRecordCardDetailQuery(command.userId(), command.recordCardId()));
    }
}
