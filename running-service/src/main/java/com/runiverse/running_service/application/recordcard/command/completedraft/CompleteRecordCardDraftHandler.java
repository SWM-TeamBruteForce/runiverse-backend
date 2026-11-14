package com.runiverse.running_service.application.recordcard.command.completedraft;

import com.runiverse.running_service.application.common.port.out.UploadedImage;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardImageException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftInProgressException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageNotUploadedException;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.out.CompleteMultipartUploadPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardImagePort;
import com.runiverse.running_service.application.recordcard.port.out.LockRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardDraftPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 9-2 확정 — 트랜잭션을 걸지 않는다. S3 확인은 트랜잭션 밖에서 먼저 끝내고, DB 저장만 RecordCardDraftSaver가 짧게 연다
@Service
@RequiredArgsConstructor
public class CompleteRecordCardDraftHandler implements CompleteRecordCardDraftUsecase {

    private final RecordCardDraftProperties properties;
    private final LoadRecordCardDraftPort loadRecordCardDraftPort;
    private final LockRecordCardDraftPort lockRecordCardDraftPort;
    private final UpdateRecordCardDraftPort updateRecordCardDraftPort;
    private final LoadRecordCardImagePort loadRecordCardImagePort;
    private final CompleteMultipartUploadPort completeMultipartUploadPort;
    private final RecordCardDraftSaver recordCardDraftSaver;

    @Override
    public CompleteRecordCardDraftResult handle(CompleteRecordCardDraftCommand command) {
        // 1. 초안이 있고 내 것인지(404) — 잠그기 전에 걸러 남의 초안에 잠금을 남기지 않는다
        loadOwnDraft(command);

        // 2. 같은 초안의 확정은 하나만 — 이미 누가 처리 중이면 기다리지 않고 409(다시 부를지는 앱이 정한다)
        String token = lockRecordCardDraftPort.tryLock(command.recordCardDraftId(), properties.lockTtl())
                .orElseThrow(RecordCardDraftInProgressException::new);
        try {
            // 3. 잠근 뒤 다시 읽는다 — 1과 2 사이에 앞 요청이 확정을 끝냈을 수 있다. 확정됐으면 같은 카드를 돌려준다
            RecordCardDraft draft = loadOwnDraft(command);
            if (draft.confirmed()) {
                return new CompleteRecordCardDraftResult(draft.recordCardId());
            }

            // 4. 사진이 S3에 다 있는지(400) — 트랜잭션 밖이다. 실패하면 아무것도 저장하지 않고 초안은 그대로 둔다
            RecordCardDraft verified = verifyUploads(draft);

            // 5. DB 저장(트랜잭션) — 기록 잠금·카드 확인·순위·저장. 이미 이 초안이 만든 카드면 저장 없이 끝난다
            recordCardDraftSaver.save(verified);

            // 6. 확정 표시 — 남은 유효 시간은 그대로다. 그사이 초안이 만료돼 표시를 못 남겨도 카드는 저장됐으니 정상 응답
            updateRecordCardDraftPort.update(verified.confirm());
            return new CompleteRecordCardDraftResult(verified.recordCardId());
        } finally {
            lockRecordCardDraftPort.unlock(command.recordCardDraftId(), token);
        }
    }

    private RecordCardDraft loadOwnDraft(CompleteRecordCardDraftCommand command) {
        return loadRecordCardDraftPort.load(command.recordCardDraftId())
                .filter(draft -> draft.userId().equals(command.userId()))
                .orElseThrow(RecordCardDraftNotFoundException::new);
    }

    // 사진마다 확인한다 — 나눠 올린 사진은 합친 뒤 바로 "합침"을 남겨, 뒤의 사진이 실패해 다시 불러도 다시 합치지 않는다
    private RecordCardDraft verifyUploads(RecordCardDraft draft) {
        RecordCardDraft current = draft;
        for (int i = 0; i < current.images().size(); i++) {
            DraftImage image = current.images().get(i);
            if (image.uploadType() == UploadType.MULTIPART && !image.merged()) {
                mergeParts(image);
                current = current.withImage(i, image.merge());
                updateRecordCardDraftPort.update(current);
            }
            verifyObject(image);
        }
        return current;
    }

    private void mergeParts(DraftImage image) {
        switch (completeMultipartUploadPort.complete(image.imageKey(), image.multipartUploadId(), image.partCount())) {
            case COMPLETED -> {
            }
            case PARTS_MISSING -> throw new RecordCardImageNotUploadedException();
            // 이미 합쳤거나(합침 표시 전에 끊긴 재시도) 정리됐다 — 뒤의 HEAD가 가른다(없으면 400)
            case UPLOAD_NOT_FOUND -> {
            }
        }
    }

    // 실물을 초안에 적힌 크기·형식과 대조한다 — 클라가 올렸다는 말을 믿지 않는다
    private void verifyObject(DraftImage image) {
        UploadedImage uploaded = loadRecordCardImagePort.load(image.imageKey())
                .orElseThrow(RecordCardImageNotUploadedException::new);
        if (uploaded.sizeBytes() != image.fileSizeBytes()
                || !image.contentType().equalsIgnoreCase(uploaded.contentType())) {
            throw new InvalidRecordCardImageException();
        }
    }
}
