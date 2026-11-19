package com.runiverse.running_service.application.recordcard.command.completemusic;

import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;

import java.util.List;
import java.util.UUID;

// 음악 생성 완료 — AI 서버가 요청 하나에 최종 결과를 한 번 보낸다.
// 메시지 형식(헤더·JSON 필드 이름)은 받는 쪽이 읽어 이 값으로 옮긴다
public record CompleteRecordCardMusicCommand(
        String topic,                   // 받은 토픽 — inbox 키 앞부분(토픽마다 키를 정하는 쪽이 달라서)
        String eventId,                 // 헤더 id(= inference_id) — 같은 이벤트를 두 번 처리하지 않게 거르는 키
        UUID recordCardId,
        MusicStatus result,             // 요청 단위 결과 — COMPLETED(성공)·FAILED(실패)
        String baseMusicKey,            // 성공이면 기본곡 S3 key, 실패면 null
        List<PhotoMusic> photoMusics,   // 결과에 담긴 사진만 — 담기지 않은 사진은 다른 요청의 것일 수 있어 건드리지 않는다
        String failedStage,             // 실패 단계(INVOKE·INFERENCE) — 로그용, 성공이면 null
        String failureCode,             // 실패 원인 코드(result.error.code) — 로그용, 성공이면 null
        String payload                  // 받은 본문 원문 — inbox에 그대로 남긴다
) {

    public CompleteRecordCardMusicCommand {
        photoMusics = List.copyOf(photoMusics);
    }

    // 사진 하나의 결과 — 실패면 musicKey가 null
    public record PhotoMusic(UUID recordCardImageId, String musicKey) {

    }
}
