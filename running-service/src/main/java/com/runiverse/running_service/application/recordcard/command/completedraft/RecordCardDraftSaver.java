package com.runiverse.running_service.application.recordcard.command.completedraft;

import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftInProgressException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadMaxDisplayOrderPort;
import com.runiverse.running_service.application.recordcard.port.out.LockOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 확정(9-2)의 DB 저장 단계 — 이 메서드만 트랜잭션이다. S3 확인은 핸들러가 트랜잭션 밖에서 먼저 끝낸다
// (S3를 부르는 동안 DB 연결·기록 잠금을 쥐지 않으려고 빈을 나눴다 — 같은 클래스 안 호출은 트랜잭션 프록시를 타지 않는다)
@Component
@RequiredArgsConstructor
public class RecordCardDraftSaver {

    private final LockOwnRunningRecordPort lockOwnRunningRecordPort;
    private final FindRecordCardIdByRunningRecordPort findRecordCardIdByRunningRecordPort;
    private final LoadMaxDisplayOrderPort loadMaxDisplayOrderPort;
    private final CreateRecordCardPort createRecordCardPort;

    @Transactional
    public void save(RecordCardDraft draft) {
        RunningRecordId runningRecordId = new RunningRecordId(draft.runningRecordId());
        UserId userId = new UserId(draft.userId());

        // 1. 기록 행을 잠그며 다시 확인한다(404) — 카드 쓰기는 모두 이 잠금으로 줄을 선다.
        //    9-1 뒤에 방이 숨겨졌어도 여기서 걸린다
        if (!lockOwnRunningRecordPort.lockOwnedBy(runningRecordId, userId)) {
            throw new RunningRecordNotFoundException();
        }

        // 2. 카드가 이미 있으면 — 이 초안이 만든 카드면 저장은 끝난 것이다(커밋 뒤 확정 표시 전에 끊긴 재시도).
        //    다른 초안이 먼저 만든 카드면 409
        Optional<RecordCardId> existing = findRecordCardIdByRunningRecordPort.findIdByRunningRecord(runningRecordId);
        if (existing.isPresent()) {
            if (existing.get().value().equals(draft.recordCardId())) {
                return;
            }
            throw new RecordCardAlreadyExistsException();
        }

        // 3. 순위는 내 카드 중 최댓값 + 1 — 다른 기록으로 동시에 만들면 겹칠 수 있고, 그때는 저장이 409로 끝난다
        DisplayOrder displayOrder = loadMaxDisplayOrderPort.loadMax(userId)
                .map(DisplayOrder::next)
                .orElse(DisplayOrder.FIRST);

        // 4. 도메인으로 조립한다 — 음악은 key 없이 GENERATING, 썸네일은 초안의 위치
        RecordCard recordCard = RecordCard.create(draft.recordCardId(), draft.runningRecordId(), draft.userId(),
                draft.visibility(), draft.activityType(), draft.memo(), displayOrder,
                draft.images().stream()
                        .map(image -> new RecordCard.ImageDraft(image.recordCardImageId(), image.imageKey(),
                                image.caption()))
                        .toList(),
                draft.thumbnailIndex());

        // 5. 저장한다 — 제약에 걸리면 이 트랜잭션은 더 쓸 수 없으니 예외로 롤백한다
        switch (createRecordCardPort.create(recordCard)) {
            case CREATED -> {
            }
            case DISPLAY_ORDER_TAKEN -> throw new RecordCardDraftInProgressException();
            case RECORD_ALREADY_HAS_CARD -> throw new RecordCardAlreadyExistsException();
        }
    }
}
