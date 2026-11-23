package com.runiverse.running_service.presentation.recordcard.controller;

import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.delete.DeleteRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardCommand;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.in.CreateRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.in.DeleteRecordCardUsecase;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardDetailUsecase;
import com.runiverse.running_service.application.recordcard.port.in.RetryRecordCardMusicUsecase;
import com.runiverse.running_service.application.recordcard.port.in.UpdateRecordCardUsecase;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.presentation.recordcard.request.CreateRecordCardDraftRequest;
import com.runiverse.running_service.presentation.recordcard.request.UpdateRecordCardRequest;
import com.runiverse.running_service.presentation.recordcard.response.CompleteRecordCardDraftResponse;
import com.runiverse.running_service.presentation.recordcard.response.CreateRecordCardDraftResponse;
import com.runiverse.running_service.presentation.recordcard.response.RecordCardDetailResponse;
import com.runiverse.running_service.presentation.recordcard.response.UpdateRecordCardResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 기록 카드 보기·작성·편집 — 작성은 두 단계다: 작성 요청(9-1)이 업로드 URL을 주고, 확정(9-2)이 카드를 저장한다
@RestController
@RequestMapping("record-cards")
@RequiredArgsConstructor
public class RecordCardController {

    // 초안 ID·카드 ID는 서버가 만든 UUIDv7이다 — UUID 타입으로 받으면 1-1-1-1-1 같은 값도 변환된다
    private static final String UUID_V7_FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final CreateRecordCardDraftUsecase createRecordCardDraftUsecase;
    private final CompleteRecordCardDraftUsecase completeRecordCardDraftUsecase;
    private final GetRecordCardDetailUsecase getRecordCardDetailUsecase;
    private final DeleteRecordCardUsecase deleteRecordCardUsecase;
    private final UpdateRecordCardUsecase updateRecordCardUsecase;
    private final RetryRecordCardMusicUsecase retryRecordCardMusicUsecase;

    // 8-1 — 볼 수 없는 카드(없음·탈퇴·공개 범위 밖·차단)는 모두 404다
    @GetMapping("{recordCardId}")
    public ResponseEntity<RecordCardDetailResponse> getDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = UUID_V7_FORMAT, message = "입력값이 올바르지 않습니다.")
            String recordCardId
    ) {
        UUID viewerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(RecordCardDetailResponse.from(getRecordCardDetailUsecase.handle(
                new GetRecordCardDetailQuery(viewerId, UUID.fromString(recordCardId)))));
    }

    // 9-3 — 늘 200. 새 사진이 없으면 바로 반영(draft null), 있으면 초안과 업로드 URL(draft) — 9-2로 확정하면 반영된다
    @PatchMapping("{recordCardId}")
    public ResponseEntity<UpdateRecordCardResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = UUID_V7_FORMAT, message = "입력값이 올바르지 않습니다.")
            String recordCardId,
            @Valid @RequestBody UpdateRecordCardRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<UpdateRecordCardCommand.Image> images = request.images() == null ? null : request.images().stream()
                .map(image -> new UpdateRecordCardCommand.Image(image.recordCardImageId(), image.mimeType(),
                        image.fileSizeBytes(), image.thumbnailFileSizeBytes(), image.fileName(), image.caption()))
                .toList();
        return ResponseEntity.ok(UpdateRecordCardResponse.from(updateRecordCardUsecase.handle(
                new UpdateRecordCardCommand(userId, UUID.fromString(recordCardId), request.memo(),
                        request.activityType(), request.visibility(), images, request.thumbnailIndex()))));
    }

    // 9-4 — 하드 삭제. 없거나(이미 지운 카드 포함) 남의 카드면 404다
    @DeleteMapping("{recordCardId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = UUID_V7_FORMAT, message = "입력값이 올바르지 않습니다.")
            String recordCardId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        deleteRecordCardUsecase.handle(new DeleteRecordCardCommand(userId, UUID.fromString(recordCardId)));
        return ResponseEntity.noContent().build();
    }

    // 9-6 — 204, 본문 없음. 실패한 음악만 다시 요청하고(완료된 음악은 그대로), 실패가 없으면 아무것도 하지 않는다.
    // 음악은 AI가 나중에 만든다 — 카드는 8-1로 다시 읽는다
    @PostMapping("{recordCardId}/retry")
    public ResponseEntity<Void> retry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = UUID_V7_FORMAT, message = "입력값이 올바르지 않습니다.")
            String recordCardId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        retryRecordCardMusicUsecase.handle(new RetryRecordCardMusicCommand(userId, UUID.fromString(recordCardId)));
        return ResponseEntity.noContent().build();
    }

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
            @PathVariable @Pattern(regexp = UUID_V7_FORMAT, message = "입력값이 올바르지 않습니다.")
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
                .map(image -> new CreateRecordCardDraftCommand.Image(image.mimeType(), image.fileSizeBytes(),
                        image.thumbnailFileSizeBytes(), image.fileName(), image.caption()))
                .toList();
    }
}
