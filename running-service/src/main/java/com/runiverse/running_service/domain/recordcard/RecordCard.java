package com.runiverse.running_service.domain.recordcard;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.exception.DuplicateRecordCardImageException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidDisplayOrderException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidThumbnailIndexException;
import com.runiverse.running_service.domain.recordcard.exception.MusicStatusRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardActivityTypeRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageNotInCardException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardVisibilityRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.ThumbnailNotInImagesException;
import com.runiverse.running_service.domain.recordcard.exception.TooManyRecordCardImagesException;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardMemo;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 러닝 기록 하나에 메모·사진·음악을 붙인 카드 — 기록과 1:1이다. 사진은 이 카드를 거쳐서만 바뀐다.
// 지우면 행이 사라진다(하드 삭제) — 직전 모습은 application이 이력에 남긴다. 같은 기록으로 다시 만들면 새 카드다
@Getter
public class RecordCard {

    private static final int MAX_IMAGES = 10;

    // 카드에 새로 넣을 사진 하나 — 사진 ID·사진 key는 업로드 URL을 줄 때 정한 것이다. 음악 key는 AI 서버가 나중에 준다.
    // 설명(caption)은 선택이라 null·공백이면 설명 없는 사진이 된다
    public record ImageDraft(UUID recordCardImageId, String imageKey, String caption) {

    }

    // 수정 뒤 캐러셀의 한 자리 — 남길 기존 사진이거나 새로 넣을 사진이다. 목록 순서가 곧 사진 순서다
    public sealed interface ImageSlot {

        // 이 카드에 이미 있는 사진을 남긴다 — 음악과 생성 상태는 그대로다.
        // caption은 수정 뒤의 설명이다(목록이 수정 뒤 전체이듯) — null·공백이면 설명을 지운다.
        // 설명을 바꿔도 음악은 다시 만들지 않는다
        record Keep(UUID recordCardImageId, String caption) implements ImageSlot {

        }

        // 새 사진을 넣는다 — 음악은 이제부터 만든다
        record Add(ImageDraft draft) implements ImageSlot {

        }
    }

    private final RecordCardId recordCardId;          // 작성 요청(9-1)이 정한다 — 저장 전에도 있다
    private final RunningRecordId runningRecordId;
    private final UserId userId;                      // 작성자 — 기록 주인과 같은지는 application이 대조한다
    private RecordCardVisibility visibility;
    private RecordCardActivityType activityType;
    private MusicStatus baseMusicStatus;
    private RecordCardMemo memo;                      // 회고 글 — 음악 생성이 감정·여운을 읽는다
    private DisplayOrder displayOrder;
    private RecordCardImageKey thumbnailImageKey;     // 사진이 없으면 null
    private MusicKey baseMusicKey;                    // AI 서버가 완료 알림으로 준다 — COMPLETED일 때만 있다
    private final List<RecordCardImage> images;       // 캐러셀 순서(sortOrder)대로

    @Builder
    private RecordCard(UUID recordCardId, Long runningRecordId, UUID userId, RecordCardVisibility visibility,
                       RecordCardActivityType activityType, MusicStatus baseMusicStatus, String memo, int displayOrder, String thumbnailImageKey,
                       String baseMusicKey, List<RecordCardImage> images) {
        this.recordCardId = new RecordCardId(recordCardId);
        this.runningRecordId = new RunningRecordId(runningRecordId);
        this.userId = new UserId(userId);
        this.visibility = requireVisibility(visibility);
        this.activityType = requireActivityType(activityType);
        if (baseMusicStatus == null) {
            throw new MusicStatusRequiredException();
        }
        this.baseMusicStatus = baseMusicStatus;
        this.memo = new RecordCardMemo(memo);
        this.displayOrder = new DisplayOrder(displayOrder);
        this.baseMusicKey = baseMusicKey == null ? null : new MusicKey(baseMusicKey);
        RecordCardImage.requireKeyOnlyWhenCompleted(this.baseMusicStatus, this.baseMusicKey);

        List<RecordCardImage> sorted = images == null ? List.of()
                : images.stream().sorted(Comparator.comparingInt(RecordCardImage::getSortOrder)).toList();
        validateImages(sorted);
        this.images = new ArrayList<>(sorted);

        this.thumbnailImageKey = thumbnailImageKey == null ? null : new RecordCardImageKey(thumbnailImageKey);
        validateThumbnail(this.images, this.thumbnailImageKey);
    }

    // 처음 만드는 카드 — 기본곡과 사진 음악은 모두 이제부터 만들어 key가 아직 없다. 썸네일 위치를 주지 않으면 첫 사진이다
    public static RecordCard create(UUID recordCardId, Long runningRecordId, UUID userId, RecordCardVisibility visibility,
                                    RecordCardActivityType activityType, String memo, DisplayOrder displayOrder,
                                    List<ImageDraft> imageDrafts, Integer thumbnailIndex) {
        requireOrder(displayOrder);
        List<RecordCardImage> images = addAll(imageDrafts);
        return builder()
                .recordCardId(recordCardId)
                .runningRecordId(runningRecordId)
                .userId(userId)
                .visibility(visibility)
                .activityType(activityType)
                .baseMusicStatus(MusicStatus.GENERATING)
                .memo(memo)
                .displayOrder(displayOrder.value())
                .thumbnailImageKey(thumbnailKeyAt(images, thumbnailIndex))
                .images(images)
                .build();
    }

    // DB에서 되살린다 — 사진은 RecordCardImage.restore로 만든 것을 넘긴다
    public static RecordCardBuilder restore(UUID recordCardId) {
        return builder().recordCardId(recordCardId);
    }

    public void changeMemo(String memo) {
        this.memo = new RecordCardMemo(memo);
    }

    public void changeActivityType(RecordCardActivityType activityType) {
        this.activityType = requireActivityType(activityType);
    }

    public void changeVisibility(RecordCardVisibility visibility) {
        this.visibility = requireVisibility(visibility);
    }

    // 사진 구성을 바꾼다 — 남긴 사진은 음악을 그대로 두고, 새 사진만 음악을 만든다. 목록에 없는 사진은 빠진다.
    // 썸네일 위치를 주지 않으면 지금 썸네일을 유지하되, 그 사진이 빠졌으면 첫 사진(없으면 null)으로 바꾼다
    public void rearrangeImages(List<ImageSlot> slots, Integer thumbnailIndex) {
        Objects.requireNonNull(slots);
        if (slots.size() > MAX_IMAGES) {
            throw new TooManyRecordCardImagesException();
        }

        Map<RecordCardImageId, RecordCardImage> current = new HashMap<>();
        images.forEach(image -> current.put(image.getRecordCardImageId(), image));

        Set<RecordCardImageId> keptIds = new HashSet<>();
        List<RecordCardImage> next = new ArrayList<>();
        // 남긴 사진의 새 설명 — 검사가 끝난 뒤 한꺼번에 바꾼다(없앤 설명은 null 값으로 담긴다)
        Map<RecordCardImage, RecordCardImageCaption> nextCaptions = new IdentityHashMap<>();
        for (ImageSlot slot : slots) {
            switch (slot) {
                case ImageSlot.Keep keep -> {
                    RecordCardImageId id = new RecordCardImageId(keep.recordCardImageId());
                    if (!keptIds.add(id)) {
                        throw new DuplicateRecordCardImageException();
                    }
                    RecordCardImage image = current.get(id);
                    if (image == null) {
                        throw new RecordCardImageNotInCardException();
                    }
                    nextCaptions.put(image, RecordCardImageCaption.ofNullable(keep.caption()).orElse(null));
                    next.add(image);
                }
                case ImageSlot.Add add -> next.add(RecordCardImage.add(add.draft(), next.size()));
            }
        }
        validateImages(next);
        String nextThumbnail = thumbnailIndex != null
                ? thumbnailKeyAt(next, thumbnailIndex)
                : keepOrFirstThumbnail(next, thumbnailImageKey);

        // 검사를 모두 통과한 뒤에만 바꾼다 — 중간에 실패해도 카드는 그대로다
        for (int i = 0; i < next.size(); i++) {
            next.get(i).reorder(i);
        }
        nextCaptions.forEach(RecordCardImage::changeCaption);
        images.clear();
        images.addAll(next);
        this.thumbnailImageKey = nextThumbnail == null ? null : new RecordCardImageKey(nextThumbnail);
    }

    // 사진 구성은 그대로 두고 썸네일만 고른다
    public void changeThumbnail(int thumbnailIndex) {
        this.thumbnailImageKey = new RecordCardImageKey(thumbnailKeyAt(images, thumbnailIndex));
    }

    // 기본곡 생성 완료 알림 — 바꿨으면 true. COMPLETED면 AI가 올린 key를 함께 받는다.
    // 이미 끝난 음악에 온 알림은 무시하고 false(Kafka는 같은 알림을 두 번 줄 수 있다). 지운 카드는 행이 없어 여기 오지 않는다
    public boolean completeBaseMusic(MusicStatus result, String musicKey) {
        MusicStatus checked = MusicStatus.result(result);
        if (!baseMusicStatus.isGenerating()) {
            return false;
        }
        MusicKey key = RecordCardImage.keyFor(checked, musicKey);
        this.baseMusicStatus = checked;
        this.baseMusicKey = key;
        return true;
    }

    // 사진 음악 생성 완료 알림 — 사진 ID로 찾는다. 이 카드에 없는 사진(수정에서 뺀 사진)에 온 알림은 무시하고 false
    public boolean completeImageMusic(UUID recordCardImageId, MusicStatus result, String musicKey) {
        MusicStatus checked = MusicStatus.result(result);
        RecordCardImageId id = new RecordCardImageId(recordCardImageId);
        return images.stream()
                .filter(image -> image.getRecordCardImageId().equals(id))
                .findFirst()
                .map(image -> image.completeMusic(checked, musicKey))
                .orElse(false);
    }

    public Optional<MusicKey> getBaseMusicKey() {
        return Optional.ofNullable(baseMusicKey);
    }

    public Optional<RecordCardImageKey> getThumbnailImageKey() {
        return Optional.ofNullable(thumbnailImageKey);
    }

    // 밖에서는 사진 목록을 바꿀 수 없다
    public List<RecordCardImage> getImages() {
        return List.copyOf(images);
    }

    private static RecordCardVisibility requireVisibility(RecordCardVisibility visibility) {
        if (visibility == null) {
            throw new RecordCardVisibilityRequiredException();
        }
        return visibility;
    }

    private static RecordCardActivityType requireActivityType(RecordCardActivityType activityType) {
        if (activityType == null) {
            throw new RecordCardActivityTypeRequiredException();
        }
        return activityType;
    }

    private static void requireOrder(DisplayOrder displayOrder) {
        if (displayOrder == null) {
            throw new InvalidDisplayOrderException();
        }
    }

    private static List<RecordCardImage> addAll(List<ImageDraft> drafts) {
        List<ImageDraft> safeDrafts = drafts == null ? List.of() : drafts;
        if (safeDrafts.size() > MAX_IMAGES) {
            throw new TooManyRecordCardImagesException();
        }
        List<RecordCardImage> images = new ArrayList<>(safeDrafts.size());
        for (int i = 0; i < safeDrafts.size(); i++) {
            images.add(RecordCardImage.add(safeDrafts.get(i), i));
        }
        return images;
    }

    // 사진 수·사진 중복 — 사진 ID가 겹치면 완료 알림이 어느 사진의 것인지 가릴 수 없다
    private static void validateImages(List<RecordCardImage> images) {
        if (images.size() > MAX_IMAGES) {
            throw new TooManyRecordCardImagesException();
        }
        Set<RecordCardImageId> imageIds = new HashSet<>();
        Set<RecordCardImageKey> imageKeys = new HashSet<>();
        for (RecordCardImage image : images) {
            if (!imageIds.add(image.getRecordCardImageId()) || !imageKeys.add(image.getImageKey())) {
                throw new DuplicateRecordCardImageException();
            }
        }
    }

    // 사진이 있으면 썸네일은 그중 하나이고, 사진이 없으면 썸네일도 없다
    private static void validateThumbnail(List<RecordCardImage> images, RecordCardImageKey thumbnailImageKey) {
        if (images.isEmpty() != (thumbnailImageKey == null)) {
            throw new ThumbnailNotInImagesException();
        }
        if (thumbnailImageKey != null
                && images.stream().noneMatch(image -> image.getImageKey().equals(thumbnailImageKey))) {
            throw new ThumbnailNotInImagesException();
        }
    }

    private static String thumbnailKeyAt(List<RecordCardImage> images, Integer thumbnailIndex) {
        if (images.isEmpty()) {
            if (thumbnailIndex != null) {
                throw new InvalidThumbnailIndexException();
            }
            return null;
        }
        int index = thumbnailIndex == null ? 0 : thumbnailIndex;
        if (index < 0 || index >= images.size()) {
            throw new InvalidThumbnailIndexException();
        }
        return images.get(index).getImageKey().value();
    }

    private static String keepOrFirstThumbnail(List<RecordCardImage> images, RecordCardImageKey current) {
        if (images.isEmpty()) {
            return null;
        }
        boolean stillThere = current != null
                && images.stream().anyMatch(image -> image.getImageKey().equals(current));
        return stillThere ? current.value() : images.getFirst().getImageKey().value();
    }
}
