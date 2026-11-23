package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor.Changes;
import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardImageException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardThumbnailOutOfRangeException;
import com.runiverse.running_service.application.recordcard.port.out.LoadLastRecordCardHistoryPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRunningSummaryPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.application.recordcard.port.out.SnapshotRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 수정 반영 단위 테스트")
class RecordCardEditorTest {

    private static final UUID CARD_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OWNER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID IMAGE_A = UuidCreator.getTimeOrderedEpoch();
    private static final UUID IMAGE_B = UuidCreator.getTimeOrderedEpoch();
    private static final long RECORD_ID = 501L;
    private static final RunningSummary SUMMARY = new RunningSummary(5000, 1800, 360, null,
            LocalDateTime.of(2026, 10, 19, 6, 30), 61, new BigDecimal("15.0"), 22);

    @Mock
    private LoadRecordCardForUpdatePort loadRecordCardForUpdatePort;
    @Mock
    private SnapshotRecordCardPort snapshotRecordCardPort;
    @Mock
    private UpdateRecordCardPort updateRecordCardPort;
    @Mock
    private LoadRunningSummaryPort loadRunningSummaryPort;
    @Mock
    private RequestRecordCardMusicPort requestRecordCardMusicPort;
    @Mock
    private LoadLastRecordCardHistoryPort loadLastRecordCardHistoryPort;

    private RecordCardEditor editor;

    @BeforeEach
    void setUp() {
        editor = new RecordCardEditor(loadRecordCardForUpdatePort, snapshotRecordCardPort, updateRecordCardPort,
                loadRunningSummaryPort, requestRecordCardMusicPort, loadLastRecordCardHistoryPort);
    }

    @Test
    @DisplayName("없는 카드는 404이고 아무것도 쓰지 않는다")
    void notFound() {
        // given
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> editor.applyNow(OWNER_ID, CARD_ID, memo("새 회고")))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(snapshotRecordCardPort, updateRecordCardPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("남의 카드는 요청한 사람 ID로 찾아 없는 카드와 같은 404다 — 주인 확인은 잠그기 전 조회가 한다")
    void othersCard() {
        // given
        UUID stranger = UuidCreator.getTimeOrderedEpoch();
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(stranger)))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> editor.applyNow(stranger, CARD_ID, memo("새 회고")))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(snapshotRecordCardPort, updateRecordCardPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("남긴다는 사진이 이 카드 것이 아니면 400 INVALID_RECORD_CARD_IMAGE다 — 도메인 예외(500)로 넘기지 않는다")
    void foreignImage() {
        // given
        givenCard();

        // when & then
        assertThatThrownBy(() -> editor.applyNow(OWNER_ID, CARD_ID, slots(
                List.of(new ImageSlot.Keep(UuidCreator.getTimeOrderedEpoch(), null)))))
                .isInstanceOf(InvalidRecordCardImageException.class);
        verifyNoInteractions(snapshotRecordCardPort, updateRecordCardPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("사진 목록 없이 보낸 썸네일 위치가 지금 사진 수 밖이면 400 RECORD_CARD_THUMBNAIL_OUT_OF_RANGE이고 아무것도 쓰지 않는다")
    void thumbnailOutOfRangeWithoutSlots() {
        // given -> 사진 두 장인 카드
        givenCard();

        // when & then
        assertThatThrownBy(() -> editor.applyNow(OWNER_ID, CARD_ID, new Changes(null, null, null, null, 2)))
                .isInstanceOf(RecordCardThumbnailOutOfRangeException.class);
        verifyNoInteractions(snapshotRecordCardPort, updateRecordCardPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("바뀐 것이 없으면 이력·저장·음악 요청을 하지 않는다")
    void noChange() {
        // given
        givenCard();

        // when
        editor.applyNow(OWNER_ID, CARD_ID, new Changes("한강 한 바퀴", null, RecordCardVisibility.PUBLIC, null, null));

        // then
        verifyNoInteractions(snapshotRecordCardPort, updateRecordCardPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("순서만 바꾸면 이력을 남기고 저장하지만 음악은 요청하지 않는다")
    void orderOnly() {
        // given
        givenCard();

        // when
        editor.applyNow(OWNER_ID, CARD_ID, slots(List.of(
                new ImageSlot.Keep(IMAGE_B, null), new ImageSlot.Keep(IMAGE_A, "노을"))));

        // then
        InOrder order = inOrder(snapshotRecordCardPort, updateRecordCardPort);
        order.verify(snapshotRecordCardPort).snapshot(new RecordCardId(CARD_ID), RecordCardSnapshotReason.UPDATE);
        order.verify(updateRecordCardPort).update(any());
        verifyNoInteractions(requestRecordCardMusicPort, loadRunningSummaryPort);
    }

    @Test
    @DisplayName("회고 글이 바뀌면 이력 → 저장 → 사진 없는 음악 요청(기본곡만) 순서로 쓴다")
    void memoRequestsBaseMusic() {
        // given
        givenCard();
        when(loadRunningSummaryPort.load(new RunningRecordId(RECORD_ID))).thenReturn(Optional.of(SUMMARY));

        // when
        editor.applyNow(OWNER_ID, CARD_ID, memo("바람이 좋았던 한강"));

        // then
        InOrder order = inOrder(snapshotRecordCardPort, updateRecordCardPort, requestRecordCardMusicPort);
        order.verify(snapshotRecordCardPort).snapshot(new RecordCardId(CARD_ID), RecordCardSnapshotReason.UPDATE);
        order.verify(updateRecordCardPort).update(any());
        RecordCardMusicRequest request = requested();
        assertThat(request.photos()).isEmpty();
        assertThat(request.memo()).isEqualTo("바람이 좋았던 한강");
        assertThat(request.runningSummary()).isEqualTo(SUMMARY);
    }

    @Test
    @DisplayName("설명이 바뀐 사진만 음악을 요청한다 — 설명이 그대로인 사진은 싣지 않는다")
    void captionRequestsOnlyThatImage() {
        // given
        givenCard();
        when(loadRunningSummaryPort.load(new RunningRecordId(RECORD_ID))).thenReturn(Optional.of(SUMMARY));

        // when
        editor.applyNow(OWNER_ID, CARD_ID, slots(List.of(
                new ImageSlot.Keep(IMAGE_A, "노을"), new ImageSlot.Keep(IMAGE_B, "새 설명"))));

        // then
        assertThat(requested().photos()).extracting(Photo::recordCardImageId).containsExactly(IMAGE_B);
        ArgumentCaptor<RecordCard> saved = ArgumentCaptor.forClass(RecordCard.class);
        verify(updateRecordCardPort).update(saved.capture());
        assertThat(saved.getValue().getImages()).extracting(RecordCardImage::getMusicStatus)
                .containsExactly(MusicStatus.COMPLETED, MusicStatus.GENERATING);
    }

    private RecordCardMusicRequest requested() {
        ArgumentCaptor<RecordCardMusicRequest> captor = ArgumentCaptor.forClass(RecordCardMusicRequest.class);
        verify(requestRecordCardMusicPort).request(captor.capture());
        return captor.getValue();
    }

    private void givenCard() {
        RecordCard card = RecordCard.restore(CARD_ID)
                .runningRecordId(RECORD_ID)
                .userId(OWNER_ID)
                .visibility(RecordCardVisibility.PUBLIC)
                .activityType(RecordCardActivityType.DAILY)
                .baseMusicStatus(MusicStatus.COMPLETED)
                .baseMusicKey("audio/base.wav")
                .memo("한강 한 바퀴")
                .displayOrder(1)
                .thumbnailImageKey("record-cards/a.jpg")
                .images(List.of(
                        RecordCardImage.restore(IMAGE_A, "record-cards/a.jpg", "노을", "audio/a.wav",
                                MusicStatus.COMPLETED, 0),
                        RecordCardImage.restore(IMAGE_B, "record-cards/b.jpg", null, "audio/b.wav",
                                MusicStatus.COMPLETED, 1)))
                .build();
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.of(card));
    }

    private static Changes memo(String memo) {
        return new Changes(memo, null, null, null, null);
    }

    private static Changes slots(List<ImageSlot> slots) {
        return new Changes(null, null, null, slots, null);
    }
}
