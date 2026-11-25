package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardEditDraftSaver;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor.Changes;
import com.runiverse.running_service.application.recordcard.exception.RecordCardChangedException;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.EditPlan;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageDraft;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 수정 확정 저장 단위 테스트")
class RecordCardEditDraftSaverTest {

    private static final UUID CARD_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OWNER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID KEPT = UuidCreator.getTimeOrderedEpoch();
    private static final UUID ADDED = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private RecordCardEditor recordCardEditor;

    private RecordCardEditDraftSaver saver;

    @BeforeEach
    void setUp() {
        saver = new RecordCardEditDraftSaver(recordCardEditor);
    }

    @Test
    @DisplayName("마지막 수정 이력이 초안 때와 같으면 남길 사진을 확인하고, 새 사진을 초안의 key로 넣어 반영한다")
    void applies() {
        // given
        RecordCard card = card(List.of(kept()));
        when(recordCardEditor.lockOwnCard(OWNER_ID, CARD_ID, null)).thenReturn(card);
        when(recordCardEditor.lastHistoryId(card)).thenReturn(Optional.of(7L));

        // when
        saver.save(draft(7L));

        // then
        List<ImageSlot> slots = List.of(new ImageSlot.Keep(KEPT, "새 설명"),
                new ImageSlot.Add(new ImageDraft(ADDED, "k/new.jpg", "결승선")));
        InOrder order = inOrder(recordCardEditor);
        order.verify(recordCardEditor).requireKeptImagesInCard(card, slots);
        order.verify(recordCardEditor).apply(card, new Changes("새 회고", null, null, slots, 1));
    }

    @Test
    @DisplayName("초안을 만든 뒤 다른 수정이 있었으면 409이고 반영하지 않는다 — 남길 사진 확인보다 먼저 본다")
    void changedMeanwhile() {
        // given
        RecordCard card = card(List.of(kept()));
        when(recordCardEditor.lockOwnCard(OWNER_ID, CARD_ID, null)).thenReturn(card);
        when(recordCardEditor.lastHistoryId(card)).thenReturn(Optional.of(8L));

        // when & then
        assertThatThrownBy(() -> saver.save(draft(7L))).isInstanceOf(RecordCardChangedException.class);
        verify(recordCardEditor, never()).requireKeptImagesInCard(any(), any());
        verify(recordCardEditor, never()).apply(any(), any());
    }

    @Test
    @DisplayName("초안 때 이력이 없었는데 지금 생겼어도 409다")
    void firstEditMeanwhile() {
        // given
        RecordCard card = card(List.of(kept()));
        when(recordCardEditor.lockOwnCard(OWNER_ID, CARD_ID, null)).thenReturn(card);
        when(recordCardEditor.lastHistoryId(card)).thenReturn(Optional.of(1L));

        // when & then
        assertThatThrownBy(() -> saver.save(draft(null))).isInstanceOf(RecordCardChangedException.class);
    }

    @Test
    @DisplayName("새 사진이 이미 카드에 있으면 이 초안은 반영된 것이다 — 확정 표시 전에 끊긴 재시도라 다시 반영하지 않는다")
    void alreadyApplied() {
        // given
        RecordCard card = card(List.of(kept(),
                RecordCardImage.restore(ADDED, "k/new.jpg", "결승선", null, MusicStatus.GENERATING, 1)));
        when(recordCardEditor.lockOwnCard(OWNER_ID, CARD_ID, null)).thenReturn(card);

        // when
        saver.save(draft(7L));

        // then
        verify(recordCardEditor, never()).lastHistoryId(any());
        verify(recordCardEditor, never()).apply(any(), any());
    }

    private static RecordCardDraft draft(Long baseHistoryId) {
        return new RecordCardDraft(UuidCreator.getTimeOrderedEpoch(), RecordCardDraft.DraftType.UPDATE, CARD_ID,
                OWNER_ID, 501L, null, null, null,
                List.of(new DraftImage(ADDED, "k/new.jpg", "image/jpeg", 1024L, "결승선", UploadType.SINGLE, null, null,
                        false, 100L)),
                null, false,
                new EditPlan("새 회고", null, null, List.of(
                        new EditPlan.Slot(KEPT, "새 설명", false), new EditPlan.Slot(ADDED, "결승선", true)),
                        1, baseHistoryId));
    }

    private static RecordCardImage kept() {
        return RecordCardImage.restore(KEPT, "k/kept.jpg", "옛 설명", "audio/k.wav", MusicStatus.COMPLETED, 0);
    }

    private static RecordCard card(List<RecordCardImage> images) {
        return RecordCard.restore(CARD_ID)
                .runningRecordId(501L)
                .userId(OWNER_ID)
                .visibility(RecordCardVisibility.PUBLIC)
                .activityType(RecordCardActivityType.DAILY)
                .baseMusicStatus(MusicStatus.COMPLETED)
                .baseMusicKey("audio/base.wav")
                .memo("한강 한 바퀴")
                .displayOrder(1)
                .thumbnailImageKey("k/kept.jpg")
                .images(images)
                .build();
    }
}
