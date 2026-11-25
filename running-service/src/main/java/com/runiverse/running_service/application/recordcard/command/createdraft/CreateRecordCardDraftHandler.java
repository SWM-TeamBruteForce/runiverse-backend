package com.runiverse.running_service.application.recordcard.command.createdraft;

import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer.Issued;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer.NewImage;
import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.CreateRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.out.ExistsOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardDraftIdPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardIdPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftType;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 9-1 작성 요청 — 검사하고 업로드 URL을 준다. DB에는 아무것도 쓰지 않고 작성 내용은 Redis 초안에만 둔다.
// 저장소 쓰기가 없어 트랜잭션을 걸지 않는다
@Service
@RequiredArgsConstructor
public class CreateRecordCardDraftHandler implements CreateRecordCardDraftUsecase {

    private final RecordCardDraftProperties properties;
    private final ExistsOwnRunningRecordPort existsOwnRunningRecordPort;
    private final FindRecordCardIdByRunningRecordPort findRecordCardIdByRunningRecordPort;
    private final GenerateRecordCardDraftIdPort generateRecordCardDraftIdPort;
    private final GenerateRecordCardIdPort generateRecordCardIdPort;
    private final RecordCardUploadIssuer recordCardUploadIssuer;
    private final SaveRecordCardDraftPort saveRecordCardDraftPort;

    @Override
    public CreateRecordCardDraftResult handle(CreateRecordCardDraftCommand command) {
        // 1. 운영값 검사(400) — 기록을 읽기 전에 거른다. 형식·개수·글자 수는 Request DTO가 이미 봤다
        List<NewImage> newImages = command.images().stream()
                .map(image -> new NewImage(image.mimeType(), image.fileSizeBytes(), image.thumbnailFileSizeBytes(),
                        image.fileName(), image.caption()))
                .toList();
        recordCardUploadIssuer.rejectTooLarge(newImages);
        RecordCardActivityType activityType = RecordCardActivityType.valueOf(command.activityType());
        RecordCardVisibility visibility = RecordCardVisibility.valueOf(command.visibility());

        // 2. 카드를 붙일 수 있는 기록인지(404) — 없거나·남의 것이거나·숨긴 방이면 가르지 않는다
        RunningRecordId runningRecordId = new RunningRecordId(command.runningRecordId());
        if (!existsOwnRunningRecordPort.existsOwnedBy(runningRecordId, new UserId(command.userId()))) {
            throw new RunningRecordNotFoundException();
        }

        // 3. 이 기록에 카드가 있으면 409 — 사진을 올리기 전에 막는다. 최종 판정은 확정(9-2)이 잠금 안에서 다시 한다.
        //    카드 ID는 지금 정한다 — 사진 key의 카드 폴더라 업로드 URL보다 먼저 있어야 한다
        if (findRecordCardIdByRunningRecordPort.findIdByRunningRecord(runningRecordId).isPresent()) {
            throw new RecordCardAlreadyExistsException();
        }
        UUID recordCardId = generateRecordCardIdPort.generate();

        // 4. 사진마다 ID·key를 정하고 업로드 URL을 준다 — 크기에 따라 한 번에 또는 조각으로
        Duration ttl = properties.ttl();
        List<DraftImage> draftImages = new ArrayList<>();
        List<Upload> uploads = new ArrayList<>();
        for (NewImage image : newImages) {
            Issued issued = recordCardUploadIssuer.issue(command.userId(), recordCardId, image);
            draftImages.add(issued.draftImage());
            uploads.add(issued.upload());
        }

        // 5. 초안을 저장한다 — 썸네일은 사진이 있으면 기본 첫 사진, 없으면 null
        UUID recordCardDraftId = generateRecordCardDraftIdPort.generate();
        Integer thumbnailIndex = draftImages.isEmpty() ? null
                : command.thumbnailIndex() == null ? 0 : command.thumbnailIndex();
        RecordCardDraft draft = new RecordCardDraft(recordCardDraftId, DraftType.CREATE, recordCardId,
                command.userId(), command.runningRecordId(), activityType, command.memo(), visibility, draftImages,
                thumbnailIndex, false);
        LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);
        saveRecordCardDraftPort.save(draft, ttl);

        return new CreateRecordCardDraftResult(recordCardDraftId, expiresAt, uploads);
    }
}
