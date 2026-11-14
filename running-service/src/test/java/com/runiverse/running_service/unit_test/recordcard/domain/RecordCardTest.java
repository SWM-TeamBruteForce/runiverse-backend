package com.runiverse.running_service.unit_test.recordcard.domain;

import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageDraft;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyStatusMismatchException;
import com.runiverse.running_service.domain.recordcard.exception.DuplicateRecordCardImageException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidDisplayOrderException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidThumbnailIndexException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardActivityTypeRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageCaptionTooLongException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageNotInCardException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoRequiredException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RecordCardTest {

    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final Long RUNNING_RECORD_ID = 501L;
    private static final String BASE_MUSIC_KEY = "record-cards/u/music/base";
    private static final String MEMO = "힘들었지만 끝까지 달려서 뿌듯했다";

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("사진 순서대로 자리를 매기고, 음악은 모두 생성 중이며, 썸네일은 첫 사진이다")
        void createWithImages() {
            // when
            RecordCard card = create(List.of(draft("a"), draft("b")), null);

            // then
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.GENERATING);
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value(), RecordCardImage::getSortOrder,
                            RecordCardImage::getMusicStatus)
                    .containsExactly(
                            tuple(imageKey("a"), 0, MusicStatus.GENERATING),
                            tuple(imageKey("b"), 1, MusicStatus.GENERATING));
            assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey(imageKey("a")));
        }

        @Test
        @DisplayName("사진 설명을 함께 넣고, 비운 설명은 설명 없음으로 둔다")
        void createWithCaption() {
            // given
            List<ImageDraft> drafts = List.of(
                    new ImageDraft(imageId("a"), imageKey("a"), "다리 밑 노을"),
                    new ImageDraft(imageId("b"), imageKey("b"), "  "));

            // when
            RecordCard card = create(drafts, null);

            // then
            assertThat(card.getImages().get(0).getCaption()).contains(new RecordCardImageCaption("다리 밑 노을"));
            assertThat(card.getImages().get(1).getCaption()).isEmpty();
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
                    new ImageDraft(imageId("a"), imageKey("a"), null),
                    new ImageDraft(imageId("a"), imageKey("a"), null));

            // when & then
            assertThatThrownBy(() -> create(drafts, null))
                    .isInstanceOf(DuplicateRecordCardImageException.class);
        }

        @Test
        @DisplayName("사진 key가 달라도 사진 ID가 같으면 거부한다")
        void rejectDuplicateImageId() {
            // given -> 사진 ID가 겹치면 완료 결과가 어느 사진의 것인지 가릴 수 없다
            UUID sameId = imageId("a");
            List<ImageDraft> drafts = List.of(
                    new ImageDraft(sameId, imageKey("a"), null),
                    new ImageDraft(sameId, imageKey("b"), null));

            // when & then
            assertThatThrownBy(() -> create(drafts, null))
                    .isInstanceOf(DuplicateRecordCardImageException.class);
        }

        @Test
        @DisplayName("새 카드의 기본곡·사진 음악은 key 없이 생성 중이다 — key는 AI 서버가 완료 알림으로 준다")
        void newMusicHasNoKey() {
            // when
            RecordCard card = create(List.of(draft("a")), null);

            // then
            assertThat(card.getBaseMusicKey()).isEmpty();
            assertThat(card.getImages().getFirst().getMusicKey()).isEmpty();
        }

        @Test
        @DisplayName("공개 범위가 없으면 거부한다")
        void rejectMissingVisibility() {
            // when & then
            assertThatThrownBy(() -> RecordCard.create(CARD_ID, RUNNING_RECORD_ID, USER_ID, null,
                    RecordCardActivityType.DAILY, MEMO, DisplayOrder.FIRST, List.of(), null))
                    .isInstanceOf(RecordCardVisibilityRequiredException.class);
        }

        @Test
        @DisplayName("활동유형이 없으면 거부한다")
        void rejectMissingActivityType() {
            // when & then
            assertThatThrownBy(() -> RecordCard.create(CARD_ID, RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                    null, MEMO, DisplayOrder.FIRST, List.of(), null))
                    .isInstanceOf(RecordCardActivityTypeRequiredException.class);
        }

        @Test
        @DisplayName("회고 글이 없거나 공백뿐이면 거부한다")
        void rejectMissingMemo() {
            // when & then -> 음악 생성이 회고 글로 기본곡의 감정을 정한다
            assertThatThrownBy(() -> RecordCard.create(CARD_ID, RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                    RecordCardActivityType.DAILY, null, DisplayOrder.FIRST, List.of(), null))
                    .isInstanceOf(RecordCardMemoRequiredException.class);
            assertThatThrownBy(() -> RecordCard.create(CARD_ID, RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                    RecordCardActivityType.DAILY, "   ", DisplayOrder.FIRST, List.of(), null))
                    .isInstanceOf(RecordCardMemoRequiredException.class);
        }

        @Test
        @DisplayName("활동유형과 회고 글을 담는다")
        void keepActivityTypeAndMemo() {
            // when
            RecordCard card = RecordCard.create(CARD_ID, RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                    RecordCardActivityType.TRAVEL, MEMO, DisplayOrder.FIRST, List.of(), null);

            // then
            assertThat(card.getActivityType()).isEqualTo(RecordCardActivityType.TRAVEL);
            assertThat(card.getMemo()).isEqualTo(new RecordCardMemo(MEMO));
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
            assertThat(card.getRecordCardId()).isEqualTo(new RecordCardId(CARD_ID));
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value())
                    .containsExactly(imageKey("a"), imageKey("b"));
        }

        @Test
        @DisplayName("저장된 사진 설명을 되살리고, 비어 있으면 설명 없음으로 둔다")
        void restoreCaption() {
            // given
            List<RecordCardImage> images = List.of(
                    RecordCardImage.restore(id(1), imageKey("a"), "다리 밑 노을", musicKey("a"), MusicStatus.COMPLETED, 0),
                    RecordCardImage.restore(id(2), imageKey("b"), null, musicKey("b"), MusicStatus.COMPLETED, 1));

            // when
            RecordCard card = restored(images, imageKey("a"));

            // then
            assertThat(card.getImages().get(0).getCaption()).contains(new RecordCardImageCaption("다리 밑 노을"));
            assertThat(card.getImages().get(1).getCaption()).isEmpty();
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
            // given -> a(COMPLETED), b(FAILED), c(GENERATING)
            RecordCard card = restoredWithThreeImages();

            // when -> [c, 새 d, a] — b는 뺀다
            card.rearrangeImages(List.of(keep(3L), add("d"), keep(1L)), null);

            // then
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value(), RecordCardImage::getSortOrder,
                            RecordCardImage::getMusicStatus)
                    .containsExactly(
                            tuple(imageKey("c"), 0, MusicStatus.GENERATING),
                            tuple(imageKey("d"), 1, MusicStatus.GENERATING),
                            tuple(imageKey("a"), 2, MusicStatus.COMPLETED));
            assertThat(card.getImages().get(1).getRecordCardImageId()).isEqualTo(new RecordCardImageId(imageId("d")));
        }

        @Test
        @DisplayName("남긴 사진의 설명을 바꾸거나 지우고, 음악은 그대로 둔다")
        void changeKeptCaption() {
            // given
            RecordCard card = restored(List.of(
                    RecordCardImage.restore(id(1), imageKey("a"), "옛 설명", musicKey("a"), MusicStatus.COMPLETED, 0),
                    RecordCardImage.restore(id(2), imageKey("b"), "지울 설명", null, MusicStatus.FAILED, 1),
                    RecordCardImage.restore(id(3), imageKey("c"), "그대로", null, MusicStatus.GENERATING, 2)),
                    imageKey("a"));

            // when
            card.rearrangeImages(List.of(
                    new ImageSlot.Keep(id(1), "새 설명"),
                    new ImageSlot.Keep(id(2), "  "),
                    new ImageSlot.Keep(id(3), "그대로")), null);

            // then
            assertThat(card.getImages())
                    .extracting(image -> image.getCaption().map(RecordCardImageCaption::value).orElse(null),
                            image -> image.getMusicKey().map(MusicKey::value).orElse(null), RecordCardImage::getMusicStatus)
                    .containsExactly(
                            tuple("새 설명", musicKey("a"), MusicStatus.COMPLETED),
                            tuple(null, null, MusicStatus.FAILED),
                            tuple("그대로", null, MusicStatus.GENERATING));
        }

        @Test
        @DisplayName("남긴 사진의 설명이 100자를 넘으면 거부하고 순서·설명을 바꾸지 않는다")
        void rejectTooLongKeptCaption() {
            // given
            RecordCard card = restored(List.of(
                    RecordCardImage.restore(id(1), imageKey("a"), "옛 설명", musicKey("a"), MusicStatus.COMPLETED, 0),
                    RecordCardImage.restore(id(2), imageKey("b"), null, musicKey("b"), MusicStatus.COMPLETED, 1)),
                    imageKey("a"));

            // when & then
            assertThatThrownBy(() -> card.rearrangeImages(List.of(
                    new ImageSlot.Keep(id(2), null),
                    new ImageSlot.Keep(id(1), "가".repeat(101))), null))
                    .isInstanceOf(RecordCardImageCaptionTooLongException.class);
            assertThat(card.getImages())
                    .extracting(image -> image.getImageKey().value(),
                            image -> image.getCaption().map(RecordCardImageCaption::value).orElse(null))
                    .containsExactly(tuple(imageKey("a"), "옛 설명"), tuple(imageKey("b"), null));
        }

        @Test
        @DisplayName("다른 사진 검사에서 실패하면 남긴 사진의 설명도 바꾸지 않는다")
        void keepCaptionWhenOtherCheckFails() {
            // given
            RecordCard card = restored(List.of(
                    RecordCardImage.restore(id(1), imageKey("a"), "옛 설명", musicKey("a"), MusicStatus.COMPLETED, 0)),
                    imageKey("a"));

            // when & then — 없는 사진 99를 가리킨다
            assertThatThrownBy(() -> card.rearrangeImages(List.of(
                    new ImageSlot.Keep(id(1), "새 설명"),
                    new ImageSlot.Keep(id(99), null)), null))
                    .isInstanceOf(RecordCardImageNotInCardException.class);
            assertThat(card.getImages().get(0).getCaption()).contains(new RecordCardImageCaption("옛 설명"));
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
        @DisplayName("회고 글은 지울 수 없고, 거부해도 이전 글이 남는다")
        void rejectClearingMemo() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.changeMemo("")).isInstanceOf(RecordCardMemoRequiredException.class);
            assertThat(card.getMemo()).isEqualTo(new RecordCardMemo(MEMO));
        }

        @Test
        @DisplayName("회고 글·활동유형·공개 범위를 바꾼다")
        void changeMemoActivityTypeAndVisibility() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when
            card.changeMemo("바람이 좋았다");
            card.changeActivityType(RecordCardActivityType.RACE);
            card.changeVisibility(RecordCardVisibility.PRIVATE);

            // then
            assertThat(card.getMemo()).isEqualTo(new RecordCardMemo("바람이 좋았다"));
            assertThat(card.getActivityType()).isEqualTo(RecordCardActivityType.RACE);
            assertThat(card.getVisibility()).isEqualTo(RecordCardVisibility.PRIVATE);
        }

        @Test
        @DisplayName("활동유형을 비울 수 없다")
        void rejectClearingActivityType() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then
            assertThatThrownBy(() -> card.changeActivityType(null))
                    .isInstanceOf(RecordCardActivityTypeRequiredException.class);
            assertThat(card.getActivityType()).isEqualTo(RecordCardActivityType.DAILY);
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
    @DisplayName("음악 생성 완료")
    class CompleteMusic {

        @Test
        @DisplayName("생성 중인 기본곡이 COMPLETED가 되면 AI가 준 key를 담고, 같은 알림이 다시 오면 무시한다")
        void completeBaseMusicOnce() {
            // given
            RecordCard card = create(List.of(), null);

            // when & then
            assertThat(card.completeBaseMusic(MusicStatus.COMPLETED, "ai/music/base.wav")).isTrue();
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
            assertThat(card.getBaseMusicKey()).contains(new MusicKey("ai/music/base.wav"));
            assertThat(card.completeBaseMusic(MusicStatus.FAILED, null)).isFalse();
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        }

        @Test
        @DisplayName("실패면 key를 받아도 버린다 — 실패한 음악은 객체가 없다")
        void failedDropsKey() {
            // given
            RecordCard card = create(List.of(), null);

            // when
            card.completeBaseMusic(MusicStatus.FAILED, "ai/music/ignored.wav");

            // then
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.FAILED);
            assertThat(card.getBaseMusicKey()).isEmpty();
        }

        @Test
        @DisplayName("COMPLETED인데 key가 없으면 거부하고 상태를 바꾸지 않는다")
        void readyNeedsKey() {
            // given
            RecordCard card = create(List.of(draft("a")), null);

            // when & then
            assertThatThrownBy(() -> card.completeBaseMusic(MusicStatus.COMPLETED, null))
                    .isInstanceOf(MusicKeyRequiredException.class);
            assertThatThrownBy(() -> card.completeImageMusic(imageId("a"), MusicStatus.COMPLETED, " "))
                    .isInstanceOf(MusicKeyRequiredException.class);
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.GENERATING);
            assertThat(card.getImages().getFirst().getMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        }

        @Test
        @DisplayName("사진 ID로 온 결과는 그 사진에만 반영한다")
        void completeImageMusic() {
            // given
            RecordCard card = create(List.of(draft("a"), draft("b")), null);

            // when
            boolean changed = card.completeImageMusic(imageId("b"), MusicStatus.COMPLETED, "ai/music/b.wav");

            // then
            assertThat(changed).isTrue();
            assertThat(card.getImages()).extracting(RecordCardImage::getMusicStatus)
                    .containsExactly(MusicStatus.GENERATING, MusicStatus.COMPLETED);
            assertThat(card.getImages().get(1).getMusicKey()).contains(new MusicKey("ai/music/b.wav"));
            assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        }

        @Test
        @DisplayName("이 카드에 없는 사진에 온 결과는 무시한다")
        void ignoreUnknownImage() {
            // given
            RecordCard card = restoredWithThreeImages();

            // when & then -> 수정에서 뺀 사진의 음악이 늦게 끝난 경우다
            assertThat(card.completeImageMusic(id(99), MusicStatus.COMPLETED, "ai/music/gone.wav")).isFalse();
            assertThat(card.getImages()).extracting(RecordCardImage::getMusicStatus)
                    .containsExactly(MusicStatus.COMPLETED, MusicStatus.FAILED, MusicStatus.GENERATING);
        }

        @Test
        @DisplayName("결과로 GENERATING이 오면 거부한다")
        void rejectPendingResult() {
            // given
            RecordCard card = create(List.of(), null);

            // when & then
            assertThatThrownBy(() -> card.completeBaseMusic(MusicStatus.GENERATING, null))
                    .isInstanceOf(InvalidMusicResultException.class);
        }
    }

    @Nested
    @DisplayName("음악 key와 상태")
    class MusicKeyAndStatus {

        @Test
        @DisplayName("저장값에서 COMPLETED인데 key가 없거나, 생성 중·실패인데 key가 있으면 거부한다")
        void rejectMismatchOnRestore() {
            // when & then
            assertThatThrownBy(() -> RecordCardImage.restore(id(1), imageKey("a"), null, null, MusicStatus.COMPLETED, 0))
                    .isInstanceOf(MusicKeyStatusMismatchException.class);
            assertThatThrownBy(() -> RecordCardImage.restore(id(1), imageKey("a"), null, musicKey("a"),
                    MusicStatus.GENERATING, 0))
                    .isInstanceOf(MusicKeyStatusMismatchException.class);
            assertThatThrownBy(() -> RecordCard.restore(CARD_ID)
                    .runningRecordId(RUNNING_RECORD_ID).userId(USER_ID).visibility(RecordCardVisibility.PUBLIC)
                    .activityType(RecordCardActivityType.DAILY).memo(MEMO)
                    .baseMusicStatus(MusicStatus.FAILED).displayOrder(3).baseMusicKey(BASE_MUSIC_KEY)
                    .build())
                    .isInstanceOf(MusicKeyStatusMismatchException.class);
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
        return RecordCard.create(CARD_ID, RUNNING_RECORD_ID, USER_ID, RecordCardVisibility.PUBLIC,
                RecordCardActivityType.DAILY, MEMO, DisplayOrder.FIRST, drafts, thumbnailIndex);
    }

    // a(COMPLETED)·b(FAILED)·c(GENERATING), 썸네일은 a
    private static RecordCard restoredWithThreeImages() {
        List<RecordCardImage> images = List.of(
                RecordCardImage.restore(id(1), imageKey("a"), null, musicKey("a"), MusicStatus.COMPLETED, 0),
                RecordCardImage.restore(id(2), imageKey("b"), null, null, MusicStatus.FAILED, 1),
                RecordCardImage.restore(id(3), imageKey("c"), null, null, MusicStatus.GENERATING, 2));
        return restored(images, imageKey("a"));
    }

    private static RecordCard restored(List<RecordCardImage> images, String thumbnailImageKey) {
        return RecordCard.restore(CARD_ID)
                .runningRecordId(RUNNING_RECORD_ID)
                .userId(USER_ID)
                .visibility(RecordCardVisibility.PUBLIC)
                .activityType(RecordCardActivityType.DAILY)
                .memo(MEMO)
                .baseMusicStatus(MusicStatus.COMPLETED)
                .displayOrder(3)
                .thumbnailImageKey(thumbnailImageKey)
                .baseMusicKey(BASE_MUSIC_KEY)
                .images(images)
                .build();
    }

    private static RecordCardImage storedImage(long id, String name, int sortOrder) {
        return RecordCardImage.restore(id(id), imageKey(name), null, musicKey(name), MusicStatus.COMPLETED, sortOrder);
    }

    private static List<ImageDraft> drafts(int count) {
        List<ImageDraft> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(draft("n" + i));
        }
        return drafts;
    }

    private static ImageDraft draft(String name) {
        return new ImageDraft(imageId(name), imageKey(name), null);
    }

    private static ImageSlot keep(long recordCardImageId) {
        return new ImageSlot.Keep(id(recordCardImageId), null);
    }

    private static ImageSlot add(String name) {
        return new ImageSlot.Add(draft(name));
    }

    // 저장된 사진 ID — 숫자로 구분만 한다
    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    // 새 사진 ID — 이름이 같으면 같은 ID다
    private static UUID imageId(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    private static String imageKey(String name) {
        return "record-cards/u/images/" + name + ".jpg";
    }

    private static String musicKey(String name) {
        return "record-cards/u/music/" + name;
    }
}
