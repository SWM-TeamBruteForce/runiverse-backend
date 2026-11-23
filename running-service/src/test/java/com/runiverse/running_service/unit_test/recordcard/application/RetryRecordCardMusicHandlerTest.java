package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicHandler;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRunningSummaryPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardMusicPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 음악 다시 만들기(9-6) 단위 테스트")
class RetryRecordCardMusicHandlerTest {

    private static final UUID CARD_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OWNER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID IMAGE_A = UuidCreator.getTimeOrderedEpoch();
    private static final UUID IMAGE_B = UuidCreator.getTimeOrderedEpoch();
    private static final long RECORD_ID = 501L;
    private static final RunningSummary SUMMARY = new RunningSummary(5000, 1800, 360, null,
            LocalDateTime.of(2026, 11, 3, 7, 10), 61, new BigDecimal("15.0"), null);

    @Mock
    private LoadRecordCardForUpdatePort loadRecordCardForUpdatePort;
    @Mock
    private SaveRecordCardMusicPort saveRecordCardMusicPort;
    @Mock
    private LoadRunningSummaryPort loadRunningSummaryPort;
    @Mock
    private RequestRecordCardMusicPort requestRecordCardMusicPort;

    private RetryRecordCardMusicHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RetryRecordCardMusicHandler(loadRecordCardForUpdatePort, saveRecordCardMusicPort,
                loadRunningSummaryPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("실패한 사진만 생성 중으로 저장한 뒤 그 사진만 실어 요청한다 — 완료된 기본곡·사진은 그대로")
    void retriesFailedOnly() {
        // given — 기본곡·a는 완료, b만 실패
        RecordCard card = card(OWNER_ID, MusicStatus.COMPLETED, MusicStatus.FAILED);
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.of(card));
        when(loadRunningSummaryPort.load(card.getRunningRecordId())).thenReturn(Optional.of(SUMMARY));

        // when
        handler.handle(new RetryRecordCardMusicCommand(OWNER_ID, CARD_ID));

        // then — 상태를 먼저 쓰고 요청한다
        InOrder order = inOrder(saveRecordCardMusicPort, requestRecordCardMusicPort);
        order.verify(saveRecordCardMusicPort).saveMusic(card);
        ArgumentCaptor<RecordCardMusicRequest> captor = ArgumentCaptor.forClass(RecordCardMusicRequest.class);
        order.verify(requestRecordCardMusicPort).request(captor.capture());

        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(card.getImages()).extracting(RecordCardImage::getMusicStatus)
                .containsExactly(MusicStatus.COMPLETED, MusicStatus.GENERATING);
        assertThat(captor.getValue().recordCardId()).isEqualTo(CARD_ID);
        assertThat(captor.getValue().runningSummary()).isSameAs(SUMMARY);
        assertThat(captor.getValue().photos()).extracting(RecordCardMusicRequest.Photo::recordCardImageId)
                .containsExactly(IMAGE_B);
    }

    @Test
    @DisplayName("실패한 음악이 없으면 저장도 요청도 하지 않는다 — 두 번 누르거나 그사이 끝난 경우")
    void nothingFailed() {
        // given
        RecordCard card = card(OWNER_ID, MusicStatus.COMPLETED, MusicStatus.GENERATING);
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.of(card));

        // when
        handler.handle(new RetryRecordCardMusicCommand(OWNER_ID, CARD_ID));

        // then
        verifyNoInteractions(saveRecordCardMusicPort, loadRunningSummaryPort, requestRecordCardMusicPort);
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
    }

    @Test
    @DisplayName("없는 카드(지운 카드 포함)는 404이고 아무것도 쓰지 않는다")
    void notFound() {
        // given
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new RetryRecordCardMusicCommand(OWNER_ID, CARD_ID))).isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(saveRecordCardMusicPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("남의 카드는 요청한 사람 ID로 찾아 404이고 아무것도 쓰지 않는다 — 주인 확인은 잠그기 전 조회가 한다")
    void othersCard() {
        // given
        UUID stranger = UuidCreator.getTimeOrderedEpoch();
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(stranger)))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new RetryRecordCardMusicCommand(stranger, CARD_ID))).isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(saveRecordCardMusicPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("기록 수치가 없으면 요청을 만들지 못해 실패한다 — 트랜잭션이 롤백돼 상태도 남지 않는다")
    void missingSummary() {
        // given
        RecordCard card = card(OWNER_ID, MusicStatus.FAILED, MusicStatus.FAILED);
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.of(card));
        when(loadRunningSummaryPort.load(card.getRunningRecordId())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new RetryRecordCardMusicCommand(OWNER_ID, CARD_ID))).isInstanceOf(IllegalStateException.class);
        verify(requestRecordCardMusicPort, never()).request(any());
    }

    // 기본곡은 완료, 사진 a·b는 주어진 상태
    private static RecordCard card(UUID ownerId, MusicStatus imageA, MusicStatus imageB) {
        return RecordCard.restore(CARD_ID)
                .runningRecordId(RECORD_ID)
                .userId(ownerId)
                .visibility(RecordCardVisibility.PUBLIC)
                .activityType(RecordCardActivityType.DAILY)
                .baseMusicStatus(MusicStatus.COMPLETED)
                .baseMusicKey("audio/base.wav")
                .memo("한강 한 바퀴")
                .displayOrder(1)
                .thumbnailImageKey("record-cards/u/c/a.jpg")
                .images(List.of(image(IMAGE_A, "a", imageA, 0), image(IMAGE_B, "b", imageB, 1)))
                .build();
    }

    private static RecordCardImage image(UUID id, String name, MusicStatus status, int sortOrder) {
        String musicKey = status == MusicStatus.COMPLETED ? "audio/" + name + ".wav" : null;
        return RecordCardImage.restore(id, "record-cards/u/c/" + name + ".jpg", null, musicKey, status, sortOrder);
    }
}
