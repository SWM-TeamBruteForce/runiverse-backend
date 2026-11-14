package com.runiverse.running_service.domain.recordcard;

import com.runiverse.running_service.domain.recordcard.exception.MusicStatusRequiredException;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;
import lombok.Getter;

import java.util.Optional;

// 카드의 사진 한 장과 그 사진의 설명·음악 — 사진마다 음악이 반드시 하나 있다. 카드를 거쳐서만 바뀐다
@Getter
public class RecordCardImage {

    private final RecordCardImageId recordCardImageId;   // 저장 전에는 null
    private final RecordCardImageKey imageKey;
    private RecordCardImageCaption caption;              // 선택 입력 — 없으면 null. 남긴 사진도 수정에서 바뀐다
    private final MusicKey musicKey;
    private MusicStatus musicStatus;
    private int sortOrder;                               // 캐러셀 순서 — 카드가 매긴다

    private RecordCardImage(RecordCardImageId recordCardImageId, RecordCardImageKey imageKey,
                            RecordCardImageCaption caption, MusicKey musicKey,
                            MusicStatus musicStatus, int sortOrder) {
        if (musicStatus == null) {
            throw new MusicStatusRequiredException();
        }
        this.recordCardImageId = recordCardImageId;
        this.imageKey = imageKey;
        this.caption = caption;
        this.musicKey = musicKey;
        this.musicStatus = musicStatus;
        this.sortOrder = sortOrder;
    }

    // 새로 넣는 사진 — 음악은 이제부터 만든다
    static RecordCardImage add(RecordCard.ImageDraft draft, int sortOrder) {
        return new RecordCardImage(null, new RecordCardImageKey(draft.imageKey()),
                RecordCardImageCaption.ofNullable(draft.caption()).orElse(null),
                new MusicKey(draft.musicKey()), MusicStatus.PENDING, sortOrder);
    }

    // 저장된 사진을 되돌린다 — 순서가 맞는지는 카드가 본다
    public static RecordCardImage restore(Long recordCardImageId, String imageKey, String caption, String musicKey,
                                          MusicStatus musicStatus, int sortOrder) {
        return new RecordCardImage(new RecordCardImageId(recordCardImageId), new RecordCardImageKey(imageKey),
                RecordCardImageCaption.ofNullable(caption).orElse(null),
                new MusicKey(musicKey), musicStatus, sortOrder);
    }

    void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    // 설명만 바꾼다 — 음악은 옛 설명으로 만든 그대로 둔다. null이면 설명을 지운다
    void changeCaption(RecordCardImageCaption caption) {
        this.caption = caption;
    }

    // 생성 완료 알림 — 이미 끝난 음악에 온 알림(중복 전달)은 무시하고 false를 돌려준다
    boolean completeMusic(MusicStatus result) {
        MusicStatus checked = MusicStatus.result(result);
        if (!musicStatus.isPending()) {
            return false;
        }
        this.musicStatus = checked;
        return true;
    }

    public Optional<RecordCardImageId> getRecordCardImageId() {
        return Optional.ofNullable(recordCardImageId);
    }

    public Optional<RecordCardImageCaption> getCaption() {
        return Optional.ofNullable(caption);
    }
}
