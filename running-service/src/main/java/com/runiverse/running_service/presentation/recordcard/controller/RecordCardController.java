package com.runiverse.running_service.presentation.recordcard.controller;

import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.in.CreateRecordCardDraftUsecase;
import com.runiverse.running_service.presentation.recordcard.request.CreateRecordCardDraftRequest;
import com.runiverse.running_service.presentation.recordcard.response.CompleteRecordCardDraftResponse;
import com.runiverse.running_service.presentation.recordcard.response.CreateRecordCardDraftResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 기록 카드 작성·편집 — 작성은 두 단계다: 작성 요청(9-1)이 업로드 URL을 주고, 확정(9-2)이 카드를 저장한다
@RestController
@RequestMapping("record-cards")
@RequiredArgsConstructor
public class RecordCardController {

    // 초안 ID는 서버가 만든 UUIDv7이다 — UUID 타입으로 받으면 1-1-1-1-1 같은 값도 변환된다
    private static final String DRAFT_ID_FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final CreateRecordCardDraftUsecase createRecordCardDraftUsecase;
    private final CompleteRecordCardDraftUsecase completeRecordCardDraftUsecase;

    // 9-1 — 카드는 아직 없다. 작성 내용은 Redis 초안에만 두고 200으로 업로드 URL을 준다
    @PostMapping
    public ResponseEntity<CreateRecordCardDraftResponse> createDraft(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRecordCardDraftRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CreateRecordCardDraftResult result = createRecordCardDraftUsecase.handle(new CreateRecordCardDraftCommand(
                userId, request.runningRecordId(), request.activityType(), request.memo(), request.visibility(),
                toImages(request.images()), request.thumbnailIndex()));
        return ResponseEntity.ok(CreateRecordCardDraftResponse.from(result));
    }

    // 9-2 — 본문 없이 초안 ID만 받는다. 카드가 생기므로 201, 같은 초안으로 다시 불러도 같은 카드 ID다
    @PostMapping("drafts/{recordCardDraftId}/complete")
    public ResponseEntity<CompleteRecordCardDraftResponse> completeDraft(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = DRAFT_ID_FORMAT, message = "입력값이 올바르지 않습니다.")
            String recordCardDraftId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CompleteRecordCardDraftResult result = completeRecordCardDraftUsecase.handle(
                new CompleteRecordCardDraftCommand(userId, UUID.fromString(recordCardDraftId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(CompleteRecordCardDraftResponse.from(result));
    }

    // 생략한 사진 목록은 빈 목록과 같다 — Command가 null을 빈 목록으로 바꾼다
    private static List<CreateRecordCardDraftCommand.Image> toImages(List<CreateRecordCardDraftRequest.Image> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
                .map(image -> new CreateRecordCardDraftCommand.Image(
                        image.mimeType(), image.fileSizeBytes(), image.fileName(), image.caption()))
                .toList();
    }
}
