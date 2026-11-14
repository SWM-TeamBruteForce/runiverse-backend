package com.runiverse.running_service.domain.recordcard;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.exception.DeletedAtRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.DuplicateMusicKeyException;
import com.runiverse.running_service.domain.recordcard.exception.DuplicateRecordCardImageException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidDisplayOrderException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidThumbnailIndexException;
import com.runiverse.running_service.domain.recordcard.exception.MusicStatusRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardActivityTypeRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardDeletedException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageNotInCardException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardNotDeletedException;
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

import java.time.LocalDateTime;
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
// 지운 카드도 남고, 같은 기록으로 다시 만들면 이 카드를 되살린다
@Getter
public class RecordCard {

    private static final int MAX_IMAGES = 10;

    // 카드에 새로 넣을 사진 하나 — 사진 key는 업로드한 것, 음악 key는 서버가 정해 둔 것이다.
    // 설명(caption)은 선택이라 null·공백이면 설명 없는 사진이 된다
    public record ImageDraft(String imageKey, String musicKey, String caption) {

    }

    // 수정 뒤 캐러셀의 한 자리 — 남길 기존 사진이거나 새로 넣을 사진이다. 목록 순서가 곧 사진 순서다
    public sealed interface ImageSlot {

        // 이 카드에 이미 있는 사진을 남긴다 — 음악과 생성 상태는 그대로다.
        // caption은 수정 뒤의 설명이다(목록이 수정 뒤 전체이듯) — null·공백이면 설명을 지운다.
        // 설명을 바꿔도 음악은 다시 만들지 않는다
        record Keep(Long recordCardImageId, String caption) implements ImageSlot {

        }

        // 새 사진을 넣는다 — 음악은 이제부터 만든다
        record Add(ImageDraft draft) implements ImageSlot {

        }
    }

    private final RecordCardId recordCardId;          // 저장 전에는 null
    private final RunningRecordId runningRecordId;
    private final UserId userId;                      // 작성자 — 기록 주인과 같은지는 application이 대조한다
    private RecordCardVisibility visibility;
    private RecordCardActivityType activityType;
    private MusicStatus baseMusicStatus;
    private RecordCardMemo memo;                      // 회고 글 — 음악 생성이 감정·여운을 읽는다
    private DisplayOrder displayOrder;                // 지운 카드는 DELETED
    private RecordCardImageKey thumbnailImageKey;     // 사진이 없으면 null
    private MusicKey baseMusicKey;
    private final List<RecordCardImage> images;       // 캐러셀 순서(sortOrder)대로
    private LocalDateTime deletedAt;                  // 지우지 않았으면 null

    @Builder
    private RecordCard(Long recordCardId, Long runningRecordId, UUID userId, RecordCardVisibility visibility,
                       RecordCardActivityType activityType, MusicStatus baseMusicStatus, String memo, int displayOrder, String thumbnailImageKey,
                       String baseMusicKey, List<RecordCardImage> images, LocalDateTime deletedAt) {
        this.recordCardId = recordCardId == null ? null : new RecordCardId(recordCardId);
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
        this.baseMusicKey = new MusicKey(baseMusicKey);
        this.deletedAt = deletedAt;

        // 지운 카드만 순위가 -1이다
        if ((deletedAt != null) != this.displayOrder.isDeleted()) {
            throw new InvalidDisplayOrderException();
        }

        List<RecordCardImage> sorted = images == null ? List.of()
                : images.stream().sorted(Comparator.comparingInt(RecordCardImage::getSortOrder)).toList();
        validateImages(sorted, this.baseMusicKey);
        this.images = new ArrayList<>(sorted);

        this.thumbnailImageKey = thumbnailImageKey == null ? null : new RecordCardImageKey(thumbnailImageKey);
        validateThumbnail(this.images, this.thumbnailImageKey);
    }

    // 처음 만드는 카드 — 기본곡과 사진 음악은 모두 이제부터 만든다. 썸네일 위치를 주지 않으면 첫 사진이다
    public static RecordCard create(Long runningRecordId, UUID userId, RecordCardVisibility visibility,
                                    RecordCardActivityType activityType, String memo,
                                    DisplayOrder displayOrder, String baseMusicKey,
                                    List<ImageDraft> imageDrafts, Integer thumbnailIndex) {
        requireLiveOrder(displayOrder);
        List<RecordCardImage> images = addAll(imageDrafts);
        return builder()
                .runningRecordId(runningRecordId)
                .userId(userId)
                .visibility(visibility)
                .activityType(activityType)
                .baseMusicStatus(MusicStatus.PENDING)
                .memo(memo)
                .displayOrder(displayOrder.value())
                .thumbnailImageKey(thumbnailKeyAt(images, thumbnailIndex))
                .baseMusicKey(baseMusicKey)
                .images(images)
                .build();
    }

    // DB에서 되살린다 — 사진은 RecordCardImage.restore로 만든 것을 넘긴다
    public static RecordCardBuilder restore(Long recordCardId) {
        return builder().recordCardId(recordCardId);
    }

    public void changeMemo(String memo) {
        ensureNotDeleted();
        this.memo = new RecordCardMemo(memo);
    }

    public void changeActivityType(RecordCardActivityType activityType) {
        ensureNotDeleted();
        this.activityType = requireActivityType(activityType);
    }

    public void changeVisibility(RecordCardVisibility visibility) {
        ensureNotDeleted();
        this.visibility = requireVisibility(visibility);
    }

    // 사진 구성을 바꾼다 — 남긴 사진은 음악을 그대로 두고, 새 사진만 음악을 만든다. 목록에 없는 사진은 빠진다.
    // 썸네일 위치를 주지 않으면 지금 썸네일을 유지하되, 그 사진이 빠졌으면 첫 사진(없으면 null)으로 바꾼다
    public void rearrangeImages(List<ImageSlot> slots, Integer thumbnailIndex) {
        ensureNotDeleted();
        Objects.requireNonNull(slots);
        if (slots.size() > MAX_IMAGES) {
            throw new TooManyRecordCardImagesException();
        }

        Map<RecordCardImageId, RecordCardImage> current = new HashMap<>();
        images.forEach(image -> image.getRecordCardImageId().ifPresent(id -> current.put(id, image)));

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
        validateImages(next, baseMusicKey);
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
        ensureNotDeleted();
        this.thumbnailImageKey = new RecordCardImageKey(thumbnailKeyAt(images, thumbnailIndex));
    }

    // 카드는 남기고 사진은 뺀다 — 되살릴 때도 옛 사진은 쓰지 않는다
    public void delete(LocalDateTime deletedAt) {
        ensureNotDeleted();
        if (deletedAt == null) {
            throw new DeletedAtRequiredException();
        }
        this.deletedAt = deletedAt;
        this.displayOrder = DisplayOrder.DELETED;
        this.images.clear();
        this.thumbnailImageKey = null;
    }

    // 지운 카드를 새 내용으로 덮어 되살린다 — ID와 작성 시각은 그대로고, 기본곡도 다시 만든다
    public void revive(RecordCardVisibility visibility, RecordCardActivityType activityType, String memo,
                       DisplayOrder displayOrder, String baseMusicKey, List<ImageDraft> imageDrafts,
                       Integer thumbnailIndex) {
        if (!isDeleted()) {
            throw new RecordCardNotDeletedException();
        }
        requireLiveOrder(displayOrder);
        RecordCardVisibility nextVisibility = requireVisibility(visibility);
        RecordCardActivityType nextActivityType = requireActivityType(activityType);
        RecordCardMemo nextMemo = new RecordCardMemo(memo);
        MusicKey nextBaseMusicKey = new MusicKey(baseMusicKey);
        List<RecordCardImage> nextImages = addAll(imageDrafts);
        validateImages(nextImages, nextBaseMusicKey);
        String nextThumbnail = thumbnailKeyAt(nextImages, thumbnailIndex);

        this.visibility = nextVisibility;
        this.activityType = nextActivityType;
        this.memo = nextMemo;
        this.displayOrder = displayOrder;
        this.baseMusicKey = nextBaseMusicKey;
        this.baseMusicStatus = MusicStatus.PENDING;
        this.images.clear();
        this.images.addAll(nextImages);
        this.thumbnailImageKey = nextThumbnail == null ? null : new RecordCardImageKey(nextThumbnail);
        this.deletedAt = null;
    }

    // 음악 생성 완료 알림 — 바꿨으면 true. 이미 끝난 음악·없는 key·지운 카드에 온 알림은 무시하고 false
    public boolean completeMusic(String musicKey, MusicStatus result) {
        MusicStatus checked = MusicStatus.result(result);
        MusicKey key = new MusicKey(musicKey);
        if (isDeleted()) {
            return false;
        }
        if (baseMusicKey.equals(key)) {
            if (!baseMusicStatus.isPending()) {
                return false;
            }
            this.baseMusicStatus = checked;
            return true;
        }
        return images.stream()
                .filter(image -> image.getMusicKey().equals(key))
                .findFirst()
                .map(image -> image.completeMusic(checked))
                .orElse(false);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isNew() {
        return recordCardId == null;
    }

    public Optional<RecordCardId> getRecordCardId() {
        return Optional.ofNullable(recordCardId);
    }

    public Optional<RecordCardImageKey> getThumbnailImageKey() {
        return Optional.ofNullable(thumbnailImageKey);
    }

    public Optional<LocalDateTime> getDeletedAt() {
        return Optional.ofNullable(deletedAt);
    }

    // 밖에서는 사진 목록을 바꿀 수 없다
    public List<RecordCardImage> getImages() {
        return List.copyOf(images);
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new RecordCardDeletedException();
        }
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

    private static void requireLiveOrder(DisplayOrder displayOrder) {
        if (displayOrder == null || displayOrder.isDeleted()) {
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

    // 사진 수·사진 중복·음악 key 중복 — 음악 key가 겹치면 완료 알림이 어느 음악인지 가릴 수 없다
    private static void validateImages(List<RecordCardImage> images, MusicKey baseMusicKey) {
        if (images.size() > MAX_IMAGES) {
            throw new TooManyRecordCardImagesException();
        }
        Set<RecordCardImageKey> imageKeys = new HashSet<>();
        Set<MusicKey> musicKeys = new HashSet<>();
        musicKeys.add(baseMusicKey);
        for (RecordCardImage image : images) {
            if (!imageKeys.add(image.getImageKey())) {
                throw new DuplicateRecordCardImageException();
            }
            if (!musicKeys.add(image.getMusicKey())) {
                throw new DuplicateMusicKeyException();
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
