package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardDraftSaver;
import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftInProgressException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort.CreateResult;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadMaxDisplayOrderPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRunningSummaryPort;
import com.runiverse.running_service.application.recordcard.port.out.LockOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftType;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.application.recordcard.port.out.RequestRecordCardMusicPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 확정의 DB 저장 단계 단위 테스트")
class RecordCardDraftSaverTest {

    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID IMAGE_ID = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final long RECORD_ID = 501L;
    private static final RunningRecordId RECORD = new RunningRecordId(RECORD_ID);
    private static final UserId USER = new UserId(USER_ID);
    private static final RunningSummary SUMMARY = new RunningSummary(5000, 1800, 360, 168,
            LocalDateTime.of(2026, 10, 19, 6, 30), 61, new BigDecimal("15.0"), 22);

    @Mock
    private LockOwnRunningRecordPort lockOwnRunningRecordPort;
    @Mock
    private FindRecordCardIdByRunningRecordPort findRecordCardIdByRunningRecordPort;
    @Mock
    private LoadMaxDisplayOrderPort loadMaxDisplayOrderPort;
    @Mock
    private CreateRecordCardPort createRecordCardPort;
    @Mock
    private LoadRunningSummaryPort loadRunningSummaryPort;
    @Mock
    private RequestRecordCardMusicPort requestRecordCardMusicPort;

    @InjectMocks
    private RecordCardDraftSaver saver;

    @Test
    @DisplayName("기록을 잠근 뒤 초안 내용 그대로 카드를 만들고, 순위는 최댓값 + 1이다")
    void createCard() {
        // given
        given(lockOwnRunningRecordPort.lockOwnedBy(RECORD, USER)).willReturn(true);
        given(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(RECORD)).willReturn(Optional.empty());
        given(loadRunningSummaryPort.load(RECORD)).willReturn(Optional.of(SUMMARY));
        given(loadMaxDisplayOrderPort.loadMax(USER)).willReturn(Optional.of(new DisplayOrder(3)));
        given(createRecordCardPort.create(any())).willReturn(CreateResult.CREATED);

        // when
        saver.save(draft());

        // then
        ArgumentCaptor<RecordCard> created = ArgumentCaptor.forClass(RecordCard.class);
        verify(createRecordCardPort).create(created.capture());
        RecordCard card = created.getValue();
        assertThat(card.getRecordCardId()).isEqualTo(new RecordCardId(CARD_ID));
        assertThat(card.getRunningRecordId()).isEqualTo(RECORD);
        assertThat(card.getUserId()).isEqualTo(USER);
        assertThat(card.getVisibility()).isEqualTo(RecordCardVisibility.FRIENDS);
        assertThat(card.getActivityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(card.getDisplayOrder()).isEqualTo(new DisplayOrder(4));
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        assertThat(card.getThumbnailImageKey()).contains(new RecordCardImageKey("k/1/a.jpg"));
        assertThat(card.getImages()).extracting(image -> image.getRecordCardImageId().value(),
                RecordCardImage::getMusicStatus).containsExactly(
                org.assertj.core.groups.Tuple.tuple(IMAGE_ID, MusicStatus.GENERATING));
    }

    @Test
    @DisplayName("내 카드가 하나도 없으면 첫 순위(1)다")
    void firstCard() {
        // given
        given(lockOwnRunningRecordPort.lockOwnedBy(RECORD, USER)).willReturn(true);
        given(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(RECORD)).willReturn(Optional.empty());
        given(loadRunningSummaryPort.load(RECORD)).willReturn(Optional.of(SUMMARY));
        given(loadMaxDisplayOrderPort.loadMax(USER)).willReturn(Optional.empty());
        given(createRecordCardPort.create(any())).willReturn(CreateResult.CREATED);

        // when
        saver.save(draft());

        // then
        ArgumentCaptor<RecordCard> created = ArgumentCaptor.forClass(RecordCard.class);
        verify(createRecordCardPort).create(created.capture());
        assertThat(created.getValue().getDisplayOrder()).isEqualTo(DisplayOrder.FIRST);
    }

    @Test
    @DisplayName("기록을 잠그지 못하면(없음·남의 것·숨긴 방) 404이고 카드를 보지도 만들지도 않는다")
    void rejectNotOwnRecord() {
        // given
        given(lockOwnRunningRecordPort.lockOwnedBy(RECORD, USER)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> saver.save(draft())).isInstanceOf(RunningRecordNotFoundException.class);
        verifyNoInteractions(findRecordCardIdByRunningRecordPort, loadMaxDisplayOrderPort, createRecordCardPort,
                requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("이 초안이 이미 만든 카드가 있으면 저장 없이 끝난다 — 커밋 뒤 확정 표시 전에 끊긴 재시도다")
    void sameCardAlreadySaved() {
        // given
        given(lockOwnRunningRecordPort.lockOwnedBy(RECORD, USER)).willReturn(true);
        given(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(RECORD))
                .willReturn(Optional.of(new RecordCardId(CARD_ID)));

        // when
        saver.save(draft());

        // then -> 이미 커밋된 카드라 음악 요청도 그때 함께 커밋됐다 — 다시 쓰면 같은 카드의 요청이 두 번 나간다
        verifyNoInteractions(loadMaxDisplayOrderPort, createRecordCardPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("다른 초안이 먼저 만든 카드가 있으면 409이고 새로 만들지 않는다")
    void otherCardExists() {
        // given
        given(lockOwnRunningRecordPort.lockOwnedBy(RECORD, USER)).willReturn(true);
        given(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(RECORD))
                .willReturn(Optional.of(new RecordCardId(UUID.randomUUID())));

        // when & then
        assertThatThrownBy(() -> saver.save(draft())).isInstanceOf(RecordCardAlreadyExistsException.class);
        verifyNoInteractions(loadMaxDisplayOrderPort, createRecordCardPort, requestRecordCardMusicPort);
    }

    @Test
    @DisplayName("다른 기록과 순위가 겹치면 다시 시도하지 않고 409(처리 중)다")
    void displayOrderTaken() {
        // given
        givenCreateResult(CreateResult.DISPLAY_ORDER_TAKEN);

        // when & then
        assertThatThrownBy(() -> saver.save(draft())).isInstanceOf(RecordCardDraftInProgressException.class);
        verify(requestRecordCardMusicPort, never()).request(any());
    }

    @Test
    @DisplayName("저장 때 기록 UNIQUE에 걸리면 409(이미 있음)다 — 잠금이 빠졌을 때의 마지막 안전장치")
    void recordAlreadyHasCardOnInsert() {
        // given
        givenCreateResult(CreateResult.RECORD_ALREADY_HAS_CARD);

        // when & then
        assertThatThrownBy(() -> saver.save(draft())).isInstanceOf(RecordCardAlreadyExistsException.class);
        verify(requestRecordCardMusicPort, never()).request(any());
    }

    @Test
    @DisplayName("새로 만든 카드면 기본곡과 카드 사진 전부의 음악을 기록 요약과 함께 한 번 요청한다")
    void requestsMusicOnCreate() {
        // given
        givenCreateResult(CreateResult.CREATED);

        // when
        saver.save(draft());

        // then
        ArgumentCaptor<RecordCardMusicRequest> requested = ArgumentCaptor.forClass(RecordCardMusicRequest.class);
        verify(requestRecordCardMusicPort).request(requested.capture());
        RecordCardMusicRequest request = requested.getValue();
        assertThat(request.recordCardId()).isEqualTo(CARD_ID);
        assertThat(request.userId()).isEqualTo(USER_ID);
        assertThat(request.memo()).isEqualTo("바람이 좋았다");
        assertThat(request.activityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(request.runningSummary()).isEqualTo(SUMMARY);
        assertThat(request.photos()).containsExactly(new Photo(IMAGE_ID, "k/1/a.jpg", "노을"));
    }

    @Test
    @DisplayName("기록 요약을 읽지 못하면 404이고 카드도 요청도 만들지 않는다")
    void summaryMissing() {
        // given
        given(lockOwnRunningRecordPort.lockOwnedBy(RECORD, USER)).willReturn(true);
        given(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(RECORD)).willReturn(Optional.empty());
        given(loadRunningSummaryPort.load(RECORD)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> saver.save(draft())).isInstanceOf(RunningRecordNotFoundException.class);
        verifyNoInteractions(loadMaxDisplayOrderPort, createRecordCardPort, requestRecordCardMusicPort);
    }

    private void givenCreateResult(CreateResult result) {
        given(lockOwnRunningRecordPort.lockOwnedBy(RECORD, USER)).willReturn(true);
        given(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(RECORD)).willReturn(Optional.empty());
        given(loadRunningSummaryPort.load(RECORD)).willReturn(Optional.of(SUMMARY));
        given(loadMaxDisplayOrderPort.loadMax(USER)).willReturn(Optional.empty());
        given(createRecordCardPort.create(any())).willReturn(result);
    }

    private static RecordCardDraft draft() {
        return new RecordCardDraft(UUID.randomUUID(), DraftType.CREATE, CARD_ID, USER_ID, RECORD_ID,
                RecordCardActivityType.TRAVEL, "바람이 좋았다", RecordCardVisibility.FRIENDS,
                List.of(new DraftImage(IMAGE_ID, "k/1/a.jpg", "image/jpeg", 10L, "노을", UploadType.SINGLE,
                        null, null, false, 100L)),
                0, false);
    }
}
