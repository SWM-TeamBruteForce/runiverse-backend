package com.runiverse.running_service.application.recordcard.command.completemusic;

import com.runiverse.running_service.application.common.port.out.RecordInboxEventPort;
import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand.PhotoMusic;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardMusicUsecase;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardMusicPort;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 음악 생성 완료를 카드에 반영한다 — inbox 기록과 상태 변경이 한 트랜잭션이다.
// 결과에 담긴 음악만 본다 — 담기지 않은 음악은 다른 요청(생성 중에 들어온 수정·다시 만들기)의 것일 수 있다.
// GENERATING인 음악만 바꾼다 — 이미 끝난 음악에 온 결과는 늦게 온 결과라 덮지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteRecordCardMusicHandler implements CompleteRecordCardMusicUsecase {

    private final RecordInboxEventPort recordInboxEventPort;
    private final LoadRecordCardForUpdatePort loadRecordCardForUpdatePort;
    private final SaveRecordCardMusicPort saveRecordCardMusicPort;

    @Override
    @Transactional
    public void handle(CompleteRecordCardMusicCommand command) {
        UUID recordCardId = command.recordCardId();

        // 1. 처음 받은 이벤트만 처리한다 — 같은 이벤트가 다시 오면(최소 한 번 전달) 여기서 끝난다
        if (!recordInboxEventPort.recordIfAbsent(command.topic(), command.eventId(), recordCardId.toString(),
                command.payload())) {
            log.info("[기록 카드] 음악 완료 반영 건너뜀: 이미 처리한 이벤트 - recordCardId={}, eventId={}",
                    recordCardId, command.eventId());
            return;
        }

        // 2. 기록 행을 잠그고 읽는다 — 지운 카드면 반영할 곳이 없다. inbox 행은 남겨 다시 받아도 처리하지 않는다
        Optional<RecordCard> found = loadRecordCardForUpdatePort.loadForUpdate(new RecordCardId(recordCardId));
        if (found.isEmpty()) {
            log.warn("[기록 카드] 음악 완료 반영 건너뜀: 카드 없음 - recordCardId={}, eventId={}",
                    recordCardId, command.eventId());
            return;
        }
        RecordCard recordCard = found.get();

        int changedCount = 0;
        int skippedCount = 0;

        // 3. 기본곡 — 결과에 늘 담긴다(실패면 key 없이)
        if (applyBaseMusic(recordCard, command)) {
            changedCount++;
        } else {
            skippedCount++;
        }

        // 4. 사진 — 카드 사진을 ID로 모아, 결과에 담긴 사진만 찾아 바꾼다
        Map<UUID, RecordCardImage> imagesById = recordCard.getImages().stream()
                .collect(Collectors.toMap(image -> image.getRecordCardImageId().value(), Function.identity()));
        for (PhotoMusic photoMusic : command.photoMusics()) {
            if (applyPhotoMusic(recordCard, imagesById, photoMusic, command)) {
                changedCount++;
            } else {
                skippedCount++;
            }
        }

        // 5. 바뀐 음악 상태·key만 쓴다
        saveRecordCardMusicPort.saveMusic(recordCard);
        log.info("[기록 카드] 음악 완료 반영 성공 - recordCardId={}, eventId={}, result={}, changedCount={}, "
                        + "skippedCount={}, failedStage={}, failureCode={}",
                recordCardId, command.eventId(), command.result(), changedCount, skippedCount,
                command.failedStage(), command.failureCode());
    }

    private boolean applyBaseMusic(RecordCard recordCard, CompleteRecordCardMusicCommand command) {
        if (!recordCard.getBaseMusicStatus().isGenerating()) {
            log.warn("[기록 카드] 기본곡 반영 건너뜀: 이미 끝난 음악 - recordCardId={}, eventId={}, currentStatus={}, "
                            + "result={}",
                    command.recordCardId(), command.eventId(), recordCard.getBaseMusicStatus(), command.result());
            return false;
        }
        return recordCard.completeBaseMusic(command.result(), command.baseMusicKey());
    }

    private boolean applyPhotoMusic(RecordCard recordCard, Map<UUID, RecordCardImage> imagesById,
                                    PhotoMusic photoMusic, CompleteRecordCardMusicCommand command) {
        RecordCardImage image = imagesById.get(photoMusic.recordCardImageId());
        if (image == null) {
            // 수정에서 뺀 사진의 늦은 결과 — 사진 행은 작성 확정만 만든다
            log.warn("[기록 카드] 사진 음악 반영 건너뜀: 사진 없음 - recordCardId={}, recordCardImageId={}, eventId={}",
                    command.recordCardId(), photoMusic.recordCardImageId(), command.eventId());
            return false;
        }
        if (!image.getMusicStatus().isGenerating()) {
            log.warn("[기록 카드] 사진 음악 반영 건너뜀: 이미 끝난 음악 - recordCardId={}, recordCardImageId={}, "
                            + "eventId={}, currentStatus={}, result={}",
                    command.recordCardId(), photoMusic.recordCardImageId(), command.eventId(),
                    image.getMusicStatus(), command.result());
            return false;
        }
        return recordCard.completeImageMusic(photoMusic.recordCardImageId(), command.result(), photoMusic.musicKey());
    }
}
