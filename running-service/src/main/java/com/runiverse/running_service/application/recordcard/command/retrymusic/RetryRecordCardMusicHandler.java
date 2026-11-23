package com.runiverse.running_service.application.recordcard.command.retrymusic;

import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.RetryRecordCardMusicUsecase;
import com.runiverse.running_service.application.recordcard.port.out.LoadOwnRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRunningSummaryPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardMusicPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 9-6 음악 다시 만들기 — 내 카드 확인 → 기록 행 잠금 → 도메인이 되돌림 → 음악 상태 저장 → 요청(아웃박스).
// 한 트랜잭션이라 상태와 요청이 함께 커밋된다. 카드 내용이 바뀌지 않아 이력을 남기지 않는다 —
// 그래서 진행 중인 수정 초안의 확정(9-2)이 409를 받지 않는다. 돌려주는 값은 없다 — 카드는 8-1로 다시 읽는다
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryRecordCardMusicHandler implements RetryRecordCardMusicUsecase {

    private final LoadOwnRecordCardForUpdatePort loadOwnRecordCardForUpdatePort;
    private final SaveRecordCardMusicPort saveRecordCardMusicPort;
    private final LoadRunningSummaryPort loadRunningSummaryPort;
    private final RequestRecordCardMusicPort requestRecordCardMusicPort;

    @Override
    @Transactional
    public void handle(RetryRecordCardMusicCommand command) {
        UUID recordCardId = command.recordCardId();

        // 1. 내 카드면 기록 행을 잠그고 읽는다 — 없거나(지운 카드 포함) 남의 카드면 잠그지 않고 404
        RecordCard card = loadOwnRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(recordCardId),
                        new UserId(command.userId()))
                .orElseThrow(RecordCardNotFoundException::new);

        // 2. 실패한 음악이 없으면 아무것도 하지 않는다 — 응답은 같은 204다
        if (!card.retryMusic()) {
            log.info("[기록 카드] 음악 다시 만들기 건너뜀: 실패한 음악 없음 - recordCardId={}", recordCardId);
            return;
        }

        // 3. 음악 상태만 쓰고 같은 트랜잭션에서 요청을 아웃박스에 쓴다 — 사진은 카드 전부다
        saveRecordCardMusicPort.saveMusic(card);
        RecordCardMusicRequest.RunningSummary summary = loadRunningSummaryPort.load(card.getRunningRecordId())
                .orElseThrow(() -> new IllegalStateException("카드가 붙은 기록의 수치가 없습니다"));
        requestRecordCardMusicPort.request(RecordCardMusicRequest.forRetry(card, summary));
        log.info("[기록 카드] 음악 다시 만들기 요청 성공 - recordCardId={}, imageCount={}",
                recordCardId, card.getImages().size());
    }
}
