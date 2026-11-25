package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageDraft;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("기록 카드 음악 생성 요청 단위 테스트")
class RecordCardMusicRequestTest {

    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440000");
    private static final UUID IMAGE_ID_1 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final UUID IMAGE_ID_2 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e");
    private static final RunningSummary SUMMARY = new RunningSummary(5000, 1800, 360, null,
            LocalDateTime.of(2026, 10, 19, 6, 30), 61, new BigDecimal("15.0"), null);

    @Test
    @DisplayName("방금 만든 카드의 작성자·카드·회고 글·활동 유형과 기록 요약을 담는다")
    void carriesCardAndSummary() {
        // given
        RecordCard card = card(List.of());

        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.of(card, SUMMARY);

        // then
        assertThat(request.userId()).isEqualTo(USER_ID);
        assertThat(request.recordCardId()).isEqualTo(CARD_ID);
        assertThat(request.memo()).isEqualTo("한강 한 바퀴");
        assertThat(request.activityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(request.runningSummary()).isSameAs(SUMMARY);
    }

    @Test
    @DisplayName("사진은 카드 순서대로 담고, 설명이 없으면 null이다")
    void photosInCardOrder() {
        // given
        RecordCard card = card(List.of(
                new ImageDraft(IMAGE_ID_1, "record-cards/u/c/1/first.jpg", "출발선"),
                new ImageDraft(IMAGE_ID_2, "record-cards/u/c/2/second.png", null)));

        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.of(card, SUMMARY);

        // then
        assertThat(request.photos()).containsExactly(
                new Photo(IMAGE_ID_1, "record-cards/u/c/1/first.jpg", "출발선"),
                new Photo(IMAGE_ID_2, "record-cards/u/c/2/second.png", null));
    }

    @Test
    @DisplayName("사진이 없으면 빈 목록이다 — 기본곡만 요청한다")
    void noPhotos() {
        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.of(card(List.of()), SUMMARY);

        // then
        assertThat(request.photos()).isEmpty();
    }

    @Test
    @DisplayName("사진 목록은 복사해 둬 바깥에서 바꿔도 요청이 달라지지 않는다")
    void photosAreCopied() {
        // given
        List<Photo> photos = new ArrayList<>(List.of(new Photo(IMAGE_ID_1, "k", null)));
        RecordCardMusicRequest request = new RecordCardMusicRequest(USER_ID, CARD_ID, "m",
                RecordCardActivityType.DAILY, SUMMARY, photos);

        // when
        photos.clear();

        // then
        assertThat(request.photos()).hasSize(1);
        assertThatThrownBy(() -> request.photos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("수정 요청은 도메인이 정한 사진(설명이 바뀐 사진·새 사진)만 싣는다 — 설명이 그대로인 사진은 싣지 않는다")
    void forEditCarriesOnlyRegeneratedImages() {
        // given — 사진 둘, 첫 사진 설명만 바꾸고 새 사진 하나를 넣는다
        RecordCard card = card(List.of(new ImageDraft(IMAGE_ID_1, "k1", "옛 설명"), new ImageDraft(IMAGE_ID_2, "k2", null)));
        UUID added = UUID.fromString("01a0f2d4-6180-7000-8000-44665544014f");
        RecordCard.Edit edit = card.edit(null, null, null, List.of(
                new ImageSlot.Keep(IMAGE_ID_1, "새 설명"), new ImageSlot.Keep(IMAGE_ID_2, null),
                new ImageSlot.Add(new ImageDraft(added, "k3", "새 사진"))), null);

        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.forEdit(card, edit, SUMMARY);

        // then
        assertThat(request.photos()).containsExactly(
                new Photo(IMAGE_ID_1, "k1", "새 설명"), new Photo(added, "k3", "새 사진"));
        assertThat(request.recordCardId()).isEqualTo(CARD_ID);
    }

    @Test
    @DisplayName("회고 글만 바뀐 수정 요청은 사진 없이 기본곡만 싣는다")
    void forEditMemoOnly() {
        // given
        RecordCard card = card(List.of(new ImageDraft(IMAGE_ID_1, "k1", null)));
        RecordCard.Edit edit = card.edit("새 회고", null, null, null, null);

        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.forEdit(card, edit, SUMMARY);

        // then
        assertThat(request.photos()).isEmpty();
        assertThat(request.memo()).isEqualTo("새 회고");
    }

    @Test
    @DisplayName("재료가 그대로인 수정으로는 요청을 만들 수 없다 — 잘 만든 기본곡을 다시 만들게 하지 않는다")
    void forEditRejectsNoIngredientChange() {
        // given — 순서만 바꾼다
        RecordCard card = card(List.of(new ImageDraft(IMAGE_ID_1, "k1", null), new ImageDraft(IMAGE_ID_2, "k2", null)));
        RecordCard.Edit edit = card.edit(null, null, null,
                List.of(new ImageSlot.Keep(IMAGE_ID_2, null), new ImageSlot.Keep(IMAGE_ID_1, null)), null);

        // when & then
        assertThatThrownBy(() -> RecordCardMusicRequest.forEdit(card, edit, SUMMARY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("카드에 없거나 생성 중이 아닌 사진이 섞이면 요청을 만들 수 없다")
    void forEditRejectsForeignImages() {
        // given — 다른 카드의 수정 결과를 섞는다
        RecordCard card = card(List.of(new ImageDraft(IMAGE_ID_1, "k1", null)));
        RecordCard other = card(List.of(new ImageDraft(IMAGE_ID_2, "k2", "옛")));
        RecordCard.Edit foreign = other.edit(null, null, null, List.of(new ImageSlot.Keep(IMAGE_ID_2, "새")), null);

        // when & then
        assertThatThrownBy(() -> RecordCardMusicRequest.forEdit(card, foreign, SUMMARY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("다시 만들기 요청은 실패해서 되돌린 사진만 싣는다 — 완료된 사진은 싣지 않는다")
    void forRetryCarriesFailedPhotosOnly() {
        // given — 첫 사진은 완료, 둘째 사진만 실패한 카드를 다시 만든다
        RecordCard card = card(List.of(
                new ImageDraft(IMAGE_ID_1, "record-cards/u/c/1/first.jpg", "출발선"),
                new ImageDraft(IMAGE_ID_2, "record-cards/u/c/2/second.png", null)));
        card.completeBaseMusic(MusicStatus.COMPLETED, "audio/base.wav");
        card.completeImageMusic(IMAGE_ID_1, MusicStatus.COMPLETED, "audio/1.wav");
        card.completeImageMusic(IMAGE_ID_2, MusicStatus.FAILED, null);
        card.retryMusic();

        // when
        RecordCardMusicRequest request = RecordCardMusicRequest.forRetry(card, SUMMARY);

        // then
        assertThat(request.recordCardId()).isEqualTo(CARD_ID);
        assertThat(request.memo()).isEqualTo("한강 한 바퀴");
        assertThat(request.photos()).containsExactly(new Photo(IMAGE_ID_2, "record-cards/u/c/2/second.png", null));
    }

    @Test
    @DisplayName("기본곡만 실패했으면 사진 없이 기본곡만 요청한다")
    void forRetryBaseOnly() {
        // given
        RecordCard card = card(List.of(new ImageDraft(IMAGE_ID_1, "k1", null)));
        card.completeImageMusic(IMAGE_ID_1, MusicStatus.COMPLETED, "audio/1.wav");
        card.completeBaseMusic(MusicStatus.FAILED, null);
        card.retryMusic();

        // when & then
        assertThat(RecordCardMusicRequest.forRetry(card, SUMMARY).photos()).isEmpty();
    }

    @Test
    @DisplayName("되돌린 음악이 하나도 없으면(기본곡·사진 모두 끝남) 다시 만들기 요청을 만들 수 없다")
    void forRetryRejectsNothingToRegenerate() {
        // given
        RecordCard done = card(List.of(new ImageDraft(IMAGE_ID_1, "k1", null)));
        done.completeBaseMusic(MusicStatus.COMPLETED, "audio/base.wav");
        done.completeImageMusic(IMAGE_ID_1, MusicStatus.COMPLETED, "audio/1.wav");

        // when & then
        assertThatThrownBy(() -> RecordCardMusicRequest.forRetry(done, SUMMARY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RecordCard card(List<ImageDraft> drafts) {
        return RecordCard.create(CARD_ID, 501L, USER_ID, RecordCardVisibility.PUBLIC, RecordCardActivityType.TRAVEL,
                "한강 한 바퀴", DisplayOrder.FIRST, drafts, null);
    }
}
