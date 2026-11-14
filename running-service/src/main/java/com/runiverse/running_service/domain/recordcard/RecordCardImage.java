package com.runiverse.running_service.domain.recordcard;

import com.runiverse.running_service.domain.recordcard.exception.MusicKeyStatusMismatchException;
import com.runiverse.running_service.domain.recordcard.exception.MusicStatusRequiredException;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;
import lombok.Getter;

import java.util.Optional;
import java.util.UUID;

// 카드의 사진 한 장과 그 사진의 설명·음악 — 사진마다 음악이 반드시 하나 있다. 카드를 거쳐서만 바뀐다
@Getter
public class RecordCardImage {

    private final RecordCardImageId recordCardImageId;   // 사진 key를 발급할 때 정한다 — 저장 전에도 있다
    private final RecordCardImageKey imageKey;
    private RecordCardImageCaption caption;              // 선택 입력 — 없으면 null. 남긴 사진도 수정에서 바뀐다
    private MusicKey musicKey;                           // AI 서버가 완료 알림으로 준다 — COMPLETED일 때만 있다
    private MusicStatus musicStatus;
    private int sortOrder;                               // 캐러셀 순서 — 카드가 매긴다

    private RecordCardImage(RecordCardImageId recordCardImageId, RecordCardImageKey imageKey,
                            RecordCardImageCaption caption, MusicKey musicKey,
                            MusicStatus musicStatus, int sortOrder) {
        if (musicStatus == null) {
            throw new MusicStatusRequiredException();
        }
        requireKeyOnlyWhenCompleted(musicStatus, musicKey);
        this.recordCardImageId = recordCardImageId;
        this.imageKey = imageKey;
        this.caption = caption;
        this.musicKey = musicKey;
        this.musicStatus = musicStatus;
        this.sortOrder = sortOrder;
    }

    // 새로 넣는 사진 — 음악은 이제부터 만들어 key가 아직 없다
    static RecordCardImage add(RecordCard.ImageDraft draft, int sortOrder) {
        return new RecordCardImage(new RecordCardImageId(draft.recordCardImageId()),
                new RecordCardImageKey(draft.imageKey()),
                RecordCardImageCaption.ofNullable(draft.caption()).orElse(null),
                null, MusicStatus.GENERATING, sortOrder);
    }

    // 저장된 사진을 되돌린다 — 음악 key는 COMPLETED일 때만 있다. 순서가 맞는지는 카드가 본다
    public static RecordCardImage restore(UUID recordCardImageId, String imageKey, String caption, String musicKey,
                                          MusicStatus musicStatus, int sortOrder) {
        return new RecordCardImage(new RecordCardImageId(recordCardImageId), new RecordCardImageKey(imageKey),
                RecordCardImageCaption.ofNullable(caption).orElse(null),
                musicKey == null ? null : new MusicKey(musicKey), musicStatus, sortOrder);
    }

    void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    // 설명만 바꾼다 — 음악은 옛 설명으로 만든 그대로 둔다. null이면 설명을 지운다
    void changeCaption(RecordCardImageCaption caption) {
        this.caption = caption;
    }

    // 생성 완료 알림 — COMPLETED면 AI가 올린 key를 함께 받는다. 이미 끝난 음악에 온 알림(중복 전달)은 무시하고 false
    boolean completeMusic(MusicStatus result, String musicKey) {
        MusicStatus checked = MusicStatus.result(result);
        if (!musicStatus.isGenerating()) {
            return false;
        }
        MusicKey key = keyFor(checked, musicKey);
        this.musicStatus = checked;
        this.musicKey = key;
        return true;
    }

    public Optional<RecordCardImageCaption> getCaption() {
        return Optional.ofNullable(caption);
    }

    public Optional<MusicKey> getMusicKey() {
        return Optional.ofNullable(musicKey);
    }

    // COMPLETED면 key가 반드시 있고, 실패면 key를 버린다 — 실패한 음악은 객체가 없다
    static MusicKey keyFor(MusicStatus result, String musicKey) {
        return result == MusicStatus.COMPLETED ? new MusicKey(musicKey) : null;
    }

    static void requireKeyOnlyWhenCompleted(MusicStatus status, MusicKey musicKey) {
        if ((status == MusicStatus.COMPLETED) != (musicKey != null)) {
            throw new MusicKeyStatusMismatchException();
        }
    }
}
