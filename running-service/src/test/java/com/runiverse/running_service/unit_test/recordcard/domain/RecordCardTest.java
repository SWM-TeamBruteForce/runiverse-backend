package com.runiverse.running_service.unit_test.recordcard.domain;

import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageDraft;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.exception.DeletedAtRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.DuplicateMusicKeyException;
import com.runiverse.running_service.domain.recordcard.exception.DuplicateRecordCardImageException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidDisplayOrderException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidThumbnailIndexException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardDeletedException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageNotInCardException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardNotDeletedException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardVisibilityRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.ThumbnailNotInImagesException;
import com.runiverse.running_service.domain.recordcard.exception.TooManyRecordCardImagesException;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardMemo;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RecordCardTest {

    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final Long RUNNING_RECORD_ID = 501L;
    private static final String BASE_MUSIC_KEY = "record-cards/u/music/base";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 21, 0);

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("사진 순서대로 자리를 매기고, 음악은 모두 생성 중이며, 썸네일은 첫 사진이다")
        void createWithImages() {
            // when
            RecordCard card = create(List.of(draft("a"), draft("b")), null);

            // then
            assertThat(card.isNew()).isTrue();
            assertThat(card.isDeleted()).isFalse();
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.PENDING);
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value(), RecordCardImage::getSortOrder,
                            RecordCardImage::getMusicStatus)
                    .containsExactly(
                            tuple(imageKey("a"), 0, MusicStatus.PENDING),
                            tuple(imageKey("b"), 1, MusicStatus.PENDING));
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("a")));
        }

        @Test
        @DisplayName("썸네일 위치를 주면 그 사진이 썸네일이다")
        void pickThumbnail() {
            // when
            RecordCard card = create(List.of(draft("a"), draft("b")), 1);

            // then
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("b")));
        }

        @Test
        @DisplayName("사진이 없으면 썸네일도 없다")
        void createWithoutImages() {
            // when
            RecordCard card = create(List.of(), null);

            // then
            assertThat(card.getImages()).isEmpty();
            assertThat(card.getThumbnailImageKey()).isEmpty();
        }

        @Test
        @DisplayName("사진이 없는데 썸네일 위치를 주면 거부한다")
        void rejectThumbnailWithoutImages() {
            // when & then
            assertThatThrownBy(() -> create(List.of(), 0))
                    .isInstanceOf(InvalidThumbnailIndexException.class);
        }

        @Test
        @DisplayName("썸네일 위치가 사진 범위를 벗어나면 거부한다")
        void rejectThumbnailOutOfRange() {
            // when & then
            assertThatThrownBy(() -> create(List.of(draft("a")), 1))
                    .isInstanceOf(InvalidThumbnailIndexException.class);
            assertThatThrownBy(() -> create(List.of(draft("a")), -1))
                    .isInstanceOf(InvalidThumbnailIndexException.class);
        }

        @Test
        @DisplayName("사진은 10장까지 받고 11장은 거부한다")
        void limitImageCount() {
            // when & then
            assertThat(create(drafts(10), null).getImages()).hasSize(10);
            assertThatThrownBy(() -> create(drafts(11), null))
                    .isInstanceOf(TooManyRecordCardImagesException.class);
        }

        @Test
        @DisplayName("같은 사진을 두 번 넣으면 거부한다")
        void rejectDuplicateImage() {
            // given
            List<ImageDraft> drafts = List.of(
                    new ImageDraft(imageKey("a"), musicKey("1")),
                    new ImageDraft(imageKey("a"), musicKey("2")));

            // when & then
            assertThatThrownBy(() -> create(drafts, null))
                    .isInstanceOf(DuplicateRecordCardImageException.class);
        }

        @Test
        @DisplayName("사진 음악 key가 서로 겹치거나 기본곡 key와 겹치면 거부한다")
        void rejectDuplicateMusicKey() {
            // given -> 음악 key가 겹치면 완료 알림이 어느 음악인지 가릴 수 없다
            List<ImageDraft> sameAsEachOther = List.of(
                    new ImageDraft(imageKey("a"), musicKey("1")),
                    new ImageDraft(imageKey("b"), musicKey("1")));
            List<ImageDraft> sameAsBase = List.of(new ImageDraft(imageKey("a"), BASE_MUSIC_KEY));

            // when & then
            assertThatThrownBy(() -> create(sameAsEachOther, null))
                    .isInstanceOf(DuplicateMusicKeyException.class);
            assertThatThrownBy(() -> create(sameAsBase, null))
                    .isInstanceOf(DuplicateMusicKeyException.class);
        }

        @Test
        @DisplayName("삭제 표시 순위로는 만들 수 없다")
        void rejectDeletedOrder() {
            // when & then
            assertThatThrownBy(() -> RecordCard.create(RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                    null, DisplayOrder.DELETED, BASE_MUSIC_KEY, List.of(), null))
                    .isInstanceOf(InvalidDisplayOrderException.class);
        }

        @Test
        @DisplayName("공개 범위가 없으면 거부한다")
        void rejectMissingVisibility() {
            // when & then
            assertThatThrownBy(() -> RecordCard.create(RUNNING_RECORD_ID, USER_ID, null,
                    null, DisplayOrder.FIRST, BASE_MUSIC_KEY, List.of(), null))
                    .isInstanceOf(RecordCardVisibilityRequiredException.class);
        }

        @Test
        @DisplayName("빈 메모는 메모 없음이 된다")
        void blankMemoBecomesEmpty() {
            // when
            RecordCard card = RecordCard.create(RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                    "   ", DisplayOrder.FIRST, BASE_MUSIC_KEY, List.of(), null);

            // then
            assertThat(card.getMemo()).isEmpty();
        }
    }

    @Nested
    @DisplayName("복원")
    class Restore {

        @Test
        @DisplayName("사진을 저장된 순서대로 정렬한다")
        void sortImagesByOrder() {
            // given
            List<RecordCardImage> images = List.of(storedImage(2L, "b", 1), storedImage(1L, "a", 0));

            // when
            RecordCard card = restored(images, imageKey("a"));

            // then
            assertThat(card.getRecordCardId()).contains(new RecordCardId(88L));
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value())
                    .containsExactly(imageKey("a"), imageKey("b"));
        }

        @Test
        @DisplayName("지운 카드인데 순위가 -1이 아니거나, 살아 있는데 -1이면 거부한다")
        void rejectDeletionMismatch() {
            // when & then
            assertThatThrownBy(() -> RecordCard.restore(88L)
                    .runningRecordId(RUNNING_RECORD_ID).userId(USER_ID).visibility(RecordCardVisibility.PUBLIC)
                    .baseMusicStatus(MusicStatus.READY).displayOrder(3).baseMusicKey(BASE_MUSIC_KEY)
                    .deletedAt(NOW).build())
                    .isInstanceOf(InvalidDisplayOrderException.class);
            assertThatThrownBy(() -> RecordCard.restore(88L)
                    .runningRecordId(RUNNING_RECORD_ID).userId(USER_ID).visibility(RecordCardVisibility.PUBLIC)
                    .baseMusicStatus(MusicStatus.READY).displayOrder(-1).baseMusicKey(BASE_MUSIC_KEY)
                    .build())
                    .isInstanceOf(InvalidDisplayOrderException.class);
        }

        @Test
        @DisplayName("썸네일이 사진에 없거나, 사진이 있는데 썸네일이 없으면 거부한다")
        void rejectBrokenThumbnail() {
            // given
            List<RecordCardImage> images = List.of(storedImage(1L, "a", 0));

            // when & then
            assertThatThrownBy(() -> restored(images, imageKey("x")))
                    .isInstanceOf(ThumbnailNotInImagesException.class);
            assertThatThrownBy(() -> restored(images, null))
                    .isInstanceOf(ThumbnailNotInImagesException.class);
            assertThatThrownBy(() -> restored(List.of(), imageKey("a")))
                    .isInstanceOf(ThumbnailNotInImagesException.class);
        }
    }

    @Nested
    @DisplayName("사진 구성 바꾸기")
    class Rearrange {

        @Test
        @DisplayName("남긴 사진은 음악 상태를 유지하고, 새 사진만 생성 중이며, 빠진 사진은 사라진다")
        void keepAddAndDrop() {
            // given -> a(READY), b(FAILED), c(PENDING)
            RecordCard card = restoredWithThreeImages();

            // when -> [c, 새 d, a] — b는 뺀다
            card.rearrangeImages(List.of(keep(3L), add("d"), keep(1L)), null);

            // then
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value(), RecordCardImage::getSortOrder,
                            RecordCardImage::getMusicStatus)
                    .containsExactly(
                            tuple(imageKey("c"), 0, MusicStatus.PENDING),
                            tuple(imageKey("d"), 1, MusicStatus.PENDING),
                            tuple(imageKey("a"), 2, MusicStatus.READY));
            assertThat(card.getImages().get(1).getRecordCardImageId()).isEmpty();
        }

        @Test
        @DisplayName("썸네일 사진이 남아 있으면 썸네일을 유지한다")
        void keepThumbnailWhenStillThere() {
            // given -> 썸네일은 a
            RecordCard card = restoredWithThreeImages();

            // when
            card.rearrangeImages(List.of(keep(2L), keep(1L)), null);

            // then
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("a")));
        }

        @Test
        @DisplayName("썸네일 사진을 빼면 첫 사진이 썸네일이 되고, 사진이 없으면 썸네일도 없다")
        void fallBackWhenThumbnailDropped() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when
            card.rearrangeImages(List.of(keep(3L), keep(2L)), null);

            // then
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("c")));

            // when
            card.rearrangeImages(List.of(), null);

            // then
            assertThat(card.getImages()).isEmpty();
            assertThat(card.getThumbnailImageKey()).isEmpty();
        }

        @Test
        @DisplayName("썸네일 위치를 주면 바뀐 순서 기준으로 고른다")
        void pickThumbnailInNewOrder() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when
            card.rearrangeImages(List.of(keep(1L), add("d")), 1);

            // then
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("d")));
        }

        @Test
        @DisplayName("이 카드의 사진이 아닌 ID를 남기려 하면 거부하고 카드는 그대로다")
        void rejectForeignImageAndKeepCard() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.rearrangeImages(List.of(add("d"), keep(99L)), null))
                    .isInstanceOf(RecordCardImageNotInCardException.class);
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value())
                    .containsExactly(imageKey("a"), imageKey("b"), imageKey("c"));
        }

        @Test
        @DisplayName("같은 사진을 두 번 남기려 하면 거부한다")
        void rejectKeepingTwice() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.rearrangeImages(List.of(keep(1L), keep(1L)), null))
                    .isInstanceOf(DuplicateRecordCardImageException.class);
        }

        @Test
        @DisplayName("남긴 사진과 같은 key를 새로 넣으면 거부한다")
        void rejectAddingKeptImageAgain() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.rearrangeImages(List.of(keep(1L), add("a")), null))
                    .isInstanceOf(DuplicateRecordCardImageException.class);
        }

        @Test
        @DisplayName("썸네일 위치가 틀리면 거부하고 사진 구성도 바꾸지 않는다")
        void rejectBadThumbnailAndKeepCard() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.rearrangeImages(List.of(keep(3L)), 5))
                    .isInstanceOf(InvalidThumbnailIndexException.class);
            assertThat(card.getImages()).hasSize(3);
            assertThat(card.getImages()).extracting(RecordCardImage::getSortOrder).containsExactly(0, 1, 2);
        }

        @Test
        @DisplayName("11장으로 바꾸려 하면 거부한다")
        void rejectTooMany() {
            // given
            RecordCard card = restoredWithThreeImages();
            List<ImageSlot> slots = IntStream.range(0, 11).<ImageSlot>mapToObj(i -> add("n" + i)).toList();

            // when & then
            assertThatThrownBy(() -> card.rearrangeImages(slots, null))
                    .isInstanceOf(TooManyRecordCardImagesException.class);
        }
    }

    @Nested
    @DisplayName("메모·공개 범위·썸네일")
    class Change {

        @Test
        @DisplayName("빈 메모로 바꾸면 메모를 지운다")
        void clearMemo() {
            // given
            RecordCard card = restoredWithThreeImages();
            card.changeMemo("한강");

            // when
            card.changeMemo("");

            // then
            assertThat(card.getMemo()).isEmpty();
        }

        @Test
        @DisplayName("메모와 공개 범위를 바꾼다")
        void changeMemoAndVisibility() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when
            card.changeMemo("바람이 좋았다");
            card.changeVisibility(RecordCardVisibility.PRIVATE);

            // then
            assertThat(card.getMemo()).contains(new RecordCardMemo("바람이 좋았다"));
            assertThat(card.getVisibility()).isEqualTo(RecordCardVisibility.PRIVATE);
        }

        @Test
        @DisplayName("사진 구성은 두고 썸네일만 고른다")
        void changeThumbnailOnly() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when
            card.changeThumbnail(2);

            // then
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("c")));
            assertThatThrownBy(() -> card.changeThumbnail(3))
                    .isInstanceOf(InvalidThumbnailIndexException.class);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("지우면 순위는 -1, 사진과 썸네일은 비고, 메모·기본곡은 남는다")
        void delete() {
            // given
            RecordCard card = restoredWithThreeImages();
            card.changeMemo("한강");

            // when
            card.delete(NOW);

            // then
            assertThat(card.isDeleted()).isTrue();
            assertThat(card.getDeletedAt()).contains(NOW);
            assertThat(card.getDisplayOrder()).isEqualTo(DisplayOrder.DELETED);
            assertThat(card.getImages()).isEmpty();
            assertThat(card.getThumbnailImageKey()).isEmpty();
            assertThat(card.getMemo()).contains(new RecordCardMemo("한강"));
            assertThat(card.getBaseMusicKey().value()).isEqualTo(BASE_MUSIC_KEY);
        }

        @Test
        @DisplayName("지운 카드는 다시 지우거나 고칠 수 없다")
        void rejectChangesAfterDelete() {
            // given
            RecordCard card = restoredWithThreeImages();
            card.delete(NOW);

            // when & then
            assertThatThrownBy(() -> card.delete(NOW)).isInstanceOf(RecordCardDeletedException.class);
            assertThatThrownBy(() -> card.changeMemo("x")).isInstanceOf(RecordCardDeletedException.class);
            assertThatThrownBy(() -> card.changeVisibility(RecordCardVisibility.PUBLIC))
                    .isInstanceOf(RecordCardDeletedException.class);
            assertThatThrownBy(() -> card.rearrangeImages(List.of(), null))
                    .isInstanceOf(RecordCardDeletedException.class);
            assertThatThrownBy(() -> card.changeThumbnail(0)).isInstanceOf(RecordCardDeletedException.class);
        }

        @Test
        @DisplayName("삭제 시각이 없으면 거부한다")
        void rejectMissingDeletedAt() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.delete(null)).isInstanceOf(DeletedAtRequiredException.class);
            assertThat(card.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("되살리기")
    class Revive {

        @Test
        @DisplayName("지운 카드를 새 내용으로 덮는다 — ID는 그대로고 기본곡은 새 key로 다시 만든다")
        void revive() {
            // given
            RecordCard card = restoredWithThreeImages();
            card.delete(NOW);

            // when
            card.revive(RecordCardVisibility.FRIENDS, "다시 뛰었다", new DisplayOrder(7), "record-cards/u/music/new",
                    List.of(draft("z")), null);

            // then
            assertThat(card.isDeleted()).isFalse();
            assertThat(card.getRecordCardId()).contains(new RecordCardId(88L));
            assertThat(card.getVisibility()).isEqualTo(RecordCardVisibility.FRIENDS);
            assertThat(card.getMemo()).contains(new RecordCardMemo("다시 뛰었다"));
            assertThat(card.getDisplayOrder()).isEqualTo(new DisplayOrder(7));
            assertThat(card.getBaseMusicKey().value()).isEqualTo("record-cards/u/music/new");
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.PENDING);
            assertThat(card.getImages()).extracting(image -> image.getImageKey().value())
                    .containsExactly(imageKey("z"));
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("z")));
        }

        @Test
        @DisplayName("지우지 않은 카드는 되살릴 수 없다")
        void rejectLiveCard() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.revive(RecordCardVisibility.PUBLIC, null, new DisplayOrder(7),
                    "record-cards/u/music/new", List.of(), null))
                    .isInstanceOf(RecordCardNotDeletedException.class);
        }

        @Test
        @DisplayName("되살리는 내용이 틀리면 거부하고 지운 상태 그대로다")
        void rejectBadContentAndStayDeleted() {
            // given
            RecordCard card = restoredWithThreeImages();
            card.delete(NOW);

            // when & then
            assertThatThrownBy(() -> card.revive(RecordCardVisibility.PUBLIC, null, new DisplayOrder(7),
                    "record-cards/u/music/new", List.of(draft("z")), 3))
                    .isInstanceOf(InvalidThumbnailIndexException.class);
            assertThat(card.isDeleted()).isTrue();
            assertThat(card.getDisplayOrder()).isEqualTo(DisplayOrder.DELETED);
        }
    }

    @Nested
    @DisplayName("음악 생성 완료")
    class CompleteMusic {

        @Test
        @DisplayName("생성 중인 기본곡에 결과가 오면 바꾸고, 같은 알림이 다시 오면 무시한다")
        void completeBaseMusicOnce() {
            // given
            RecordCard card = create(List.of(), null);

            // when & then
            assertThat(card.completeMusic(BASE_MUSIC_KEY, MusicStatus.READY)).isTrue();
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.READY);
            assertThat(card.completeMusic(BASE_MUSIC_KEY, MusicStatus.FAILED)).isFalse();
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.READY);
        }

        @Test
        @DisplayName("사진 음악 key로 온 결과는 그 사진에만 반영한다")
        void completeImageMusic() {
            // given
            RecordCard card = create(List.of(draft("a"), draft("b")), null);

            // when
            boolean changed = card.completeMusic(musicKey("b"), MusicStatus.FAILED);

            // then
            assertThat(changed).isTrue();
            assertThat(card.getImages()).extracting(RecordCardImage::getMusicStatus)
                    .containsExactly(MusicStatus.PENDING, MusicStatus.FAILED);
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.PENDING);
        }

        @Test
        @DisplayName("이 카드에 없는 key나 지운 카드에 온 결과는 무시한다")
        void ignoreUnknownKeyAndDeletedCard() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then -> 수정에서 뺀 사진의 음악이 늦게 끝난 경우다
            assertThat(card.completeMusic("record-cards/u/music/gone", MusicStatus.READY)).isFalse();

            // given
            card.delete(NOW);

            // when & then
            assertThat(card.completeMusic(BASE_MUSIC_KEY, MusicStatus.READY)).isFalse();
        }

        @Test
        @DisplayName("결과로 PENDING이 오면 거부한다")
        void rejectPendingResult() {
            // given
            RecordCard card = create(List.of(), null);

            // when & then
            assertThatThrownBy(() -> card.completeMusic(BASE_MUSIC_KEY, MusicStatus.PENDING))
                    .isInstanceOf(InvalidMusicResultException.class);
        }
    }

    @Test
    @DisplayName("밖에서 사진 목록을 바꿀 수 없다")
    void imagesAreReadOnly() {
        // given
        RecordCard card = restoredWithThreeImages();

        // when & then
        assertThatThrownBy(() -> card.getImages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(card.getImages()).hasSize(3);
    }

    private static RecordCard create(List<ImageDraft> drafts, Integer thumbnailIndex) {
        return RecordCard.create(RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC, null,
                DisplayOrder.FIRST, BASE_MUSIC_KEY, drafts, thumbnailIndex);
    }

    // a(READY)·b(FAILED)·c(PENDING), 썸네일은 a
    private static RecordCard restoredWithThreeImages() {
        List<RecordCardImage> images = List.of(
                RecordCardImage.restore(1L, imageKey("a"), musicKey("a"), MusicStatus.READY, 0),
                RecordCardImage.restore(2L, imageKey("b"), musicKey("b"), MusicStatus.FAILED, 1),
                RecordCardImage.restore(3L, imageKey("c"), musicKey("c"), MusicStatus.PENDING, 2));
        return restored(images, imageKey("a"));
    }

    private static RecordCard restored(List<RecordCardImage> images, String thumbnailImageKey) {
        return RecordCard.restore(88L)
                .runningRecordId(RUNNING_RECORD_ID)
                .userId(USER_ID)
                .visibility(RecordCardVisibility.PUBLIC)
                .baseMusicStatus(MusicStatus.READY)
                .displayOrder(3)
                .thumbnailImageKey(thumbnailImageKey)
                .baseMusicKey(BASE_MUSIC_KEY)
                .images(images)
                .build();
    }

    private static RecordCardImage storedImage(Long id, String name, int sortOrder) {
        return RecordCardImage.restore(id, imageKey(name), musicKey(name), MusicStatus.READY, sortOrder);
    }

    private static List<ImageDraft> drafts(int count) {
        List<ImageDraft> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(draft("n" + i));
        }
        return drafts;
    }

    private static ImageDraft draft(String name) {
        return new ImageDraft(imageKey(name), musicKey(name));
    }

    private static ImageSlot keep(Long recordCardImageId) {
        return new ImageSlot.Keep(recordCardImageId);
    }

    private static ImageSlot add(String name) {
        return new ImageSlot.Add(draft(name));
    }

    private static String imageKey(String name) {
        return "record-cards/u/images/" + name + ".jpg";
    }

    private static String musicKey(String name) {
        return "record-cards/u/music/" + name;
    }
}
