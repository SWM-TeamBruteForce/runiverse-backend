package com.runiverse.running_service.application.recordcard.command.delete;

import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.DeleteRecordCardUsecase;
import com.runiverse.running_service.application.recordcard.port.out.DeleteRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.application.recordcard.port.out.SnapshotRecordCardPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 9-4 기록 카드 삭제 — 하드 삭제다. 지우기 직전 모습을 이력으로 남기고 지운다(한 트랜잭션).
// 남은 카드의 순위는 당기지 않는다. 생성 중이던 음악의 완료는 카드가 없어 받는 쪽이 건너뛴다
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteRecordCardHandler implements DeleteRecordCardUsecase {

    private final LoadRecordCardForUpdatePort loadRecordCardForUpdatePort;
    private final SnapshotRecordCardPort snapshotRecordCardPort;
    private final DeleteRecordCardPort deleteRecordCardPort;

    @Override
    @Transactional
    public void handle(DeleteRecordCardCommand command) {
        RecordCardId recordCardId = new RecordCardId(command.recordCardId());

        // 1. 내 카드면 기록 행을 잠그고 읽는다 — 카드 쓰기(작성·수정·삭제·다시 만들기·음악 완료)는 모두 이 잠금으로 줄을 선다.
        //    없거나(이미 지운 카드 포함) 남의 카드면 잠그지 않고 404 — 남의 카드가 있는지 드러내지 않는다
        RecordCard recordCard = loadRecordCardForUpdatePort.loadOwnedForUpdate(recordCardId,
                        new UserId(command.userId()))
                .orElseThrow(RecordCardNotFoundException::new);

        // 2. 지우기 직전 모습을 남긴다 — 신고·분쟁 대응용. 삭제가 실패하면 함께 롤백된다
        snapshotRecordCardPort.snapshot(recordCardId, RecordCardSnapshotReason.DELETE);

        // 3. 지운다 — 사진 행은 CASCADE. S3 객체는 남긴다
        deleteRecordCardPort.delete(recordCardId);
        log.info("[기록 카드] 카드 삭제 성공 - recordCardId={}, imageCount={}",
                recordCardId.value(), recordCard.getImages().size());
    }
}
