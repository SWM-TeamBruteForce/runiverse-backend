package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.common.port.out.UploadedImage;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftHandler;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardDraftSaver;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardImageException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftInProgressException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageNotUploadedException;
import com.runiverse.running_service.application.recordcard.port.out.CompleteMultipartUploadPort;
import com.runiverse.running_service.application.recordcard.port.out.CompleteMultipartUploadPort.MultipartCompletion;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardImagePort;
import com.runiverse.running_service.application.recordcard.port.out.LockRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftType;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardDraftPort;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 확정(9-2) 단위 테스트")
class CompleteRecordCardDraftHandlerTest {

    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final UUID OTHER_USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440016");
    private static final UUID DRAFT_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400d1");
    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID IMAGE_ID_1 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final UUID IMAGE_ID_2 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e");
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String TOKEN = "lock-token";
    private static final long BIG = 20_971_521L;

    @Mock
    private LoadRecordCardDraftPort loadRecordCardDraftPort;
    @Mock
    private LockRecordCardDraftPort lockRecordCardDraftPort;
    @Mock
    private UpdateRecordCardDraftPort updateRecordCardDraftPort;
    @Mock
    private LoadRecordCardImagePort loadRecordCardImagePort;
    @Mock
    private CompleteMultipartUploadPort completeMultipartUploadPort;
    @Mock
    private RecordCardDraftSaver recordCardDraftSaver;

    private CompleteRecordCardDraftHandler handler;

    @BeforeEach
    void setUp() {
        RecordCardDraftProperties properties = new RecordCardDraftProperties(Duration.ofMinutes(30),
                DataSize.ofMegabytes(100), DataSize.ofMegabytes(10), LOCK_TTL);
        handler = new CompleteRecordCardDraftHandler(properties, loadRecordCardDraftPort, lockRecordCardDraftPort,
                updateRecordCardDraftPort, loadRecordCardImagePort, completeMultipartUploadPort, recordCardDraftSaver);
    }

    @Test
    @DisplayName("잠그고 사진을 확인한 뒤 저장하고, 확정 표시를 남기고 잠금을 풀며 카드 ID를 돌려준다")
    void complete() {
        // given
        RecordCardDraft draft = draft(singleImage(IMAGE_ID_1, "k/1/a.jpg"));
        givenDraft(draft);
        givenLocked();
        given(loadRecordCardImagePort.load("k/1/a.jpg")).willReturn(Optional.of(new UploadedImage(10L, "image/jpeg")));

        // when
        CompleteRecordCardDraftResult result = handler.handle(command());

        // then
        assertThat(result.recordCardId()).isEqualTo(CARD_ID);
        InOrder order = inOrder(lockRecordCardDraftPort, loadRecordCardImagePort, recordCardDraftSaver,
                updateRecordCardDraftPort);
        order.verify(lockRecordCardDraftPort).tryLock(DRAFT_ID, LOCK_TTL);
        order.verify(loadRecordCardImagePort).load("k/1/a.jpg");
        order.verify(recordCardDraftSaver).save(draft);
        order.verify(updateRecordCardDraftPort).update(draft.confirm());
        order.verify(lockRecordCardDraftPort).unlock(DRAFT_ID, TOKEN);
    }

    @Test
    @DisplayName("이미 확정된 초안이면 사진도 DB도 보지 않고 같은 카드를 돌려준다 — 응답을 못 받은 재시도")
    void alreadyConfirmed() {
        // given
        givenDraft(draft(singleImage(IMAGE_ID_1, "k/1/a.jpg")).confirm());
        givenLocked();

        // when
        CompleteRecordCardDraftResult result = handler.handle(command());

        // then
        assertThat(result.recordCardId()).isEqualTo(CARD_ID);
        verifyNoInteractions(loadRecordCardImagePort, completeMultipartUploadPort, recordCardDraftSaver);
        verify(updateRecordCardDraftPort, never()).update(any());
        verify(lockRecordCardDraftPort).unlock(DRAFT_ID, TOKEN);
    }

    @Test
    @DisplayName("초안이 없거나(만료 포함) 남의 것이면 404이고 잠그지 않는다")
    void draftNotFound() {
        // given
        given(loadRecordCardDraftPort.load(DRAFT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardDraftNotFoundException.class);

        // given -> 남의 초안
        given(loadRecordCardDraftPort.load(DRAFT_ID)).willReturn(Optional.of(new RecordCardDraft(DRAFT_ID,
                DraftType.CREATE, CARD_ID, OTHER_USER_ID, 501L, RecordCardActivityType.DAILY, "글",
                RecordCardVisibility.PUBLIC, List.of(), null, false)));

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardDraftNotFoundException.class);
        verifyNoInteractions(lockRecordCardDraftPort, recordCardDraftSaver);
    }

    @Test
    @DisplayName("같은 초안의 확정이 이미 처리 중이면 기다리지 않고 409이고, 아무것도 하지 않는다")
    void lockedByOther() {
        // given
        givenDraft(draft(singleImage(IMAGE_ID_1, "k/1/a.jpg")));
        given(lockRecordCardDraftPort.tryLock(DRAFT_ID, LOCK_TTL)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardDraftInProgressException.class);
        verifyNoInteractions(loadRecordCardImagePort, recordCardDraftSaver);
        verify(lockRecordCardDraftPort, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("사진이 S3에 없으면 400이고, 저장·확정 표시 없이 잠금만 푼다 — 초안은 그대로라 다시 올리면 된다")
    void imageNotUploaded() {
        // given
        givenDraft(draft(singleImage(IMAGE_ID_1, "k/1/a.jpg")));
        givenLocked();
        given(loadRecordCardImagePort.load("k/1/a.jpg")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardImageNotUploadedException.class);
        verifyNoInteractions(recordCardDraftSaver);
        verify(updateRecordCardDraftPort, never()).update(any());
        verify(lockRecordCardDraftPort).unlock(DRAFT_ID, TOKEN);
    }

    @Test
    @DisplayName("올라간 크기·형식이 초안과 다르면 400(올바르지 않음)이다")
    void imageMismatch() {
        // given
        givenDraft(draft(singleImage(IMAGE_ID_1, "k/1/a.jpg")));
        givenLocked();
        given(loadRecordCardImagePort.load("k/1/a.jpg")).willReturn(Optional.of(new UploadedImage(11L, "image/jpeg")));

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(InvalidRecordCardImageException.class);
        verifyNoInteractions(recordCardDraftSaver);
    }

    @Test
    @DisplayName("형식은 대소문자를 가리지 않고 대조한다")
    void contentTypeCaseInsensitive() {
        // given
        givenDraft(draft(singleImage(IMAGE_ID_1, "k/1/a.jpg")));
        givenLocked();
        given(loadRecordCardImagePort.load("k/1/a.jpg")).willReturn(Optional.of(new UploadedImage(10L, "IMAGE/JPEG")));

        // when & then
        assertThat(handler.handle(command()).recordCardId()).isEqualTo(CARD_ID);
    }

    @Test
    @DisplayName("나눠 올린 사진은 합치고 바로 '합침'을 남긴 뒤 HEAD로 확인하고, 저장에는 합친 초안을 넘긴다")
    void mergeMultipart() {
        // given
        RecordCardDraft draft = draft(multipartImage(IMAGE_ID_1, "k/1/big.png", false));
        givenDraft(draft);
        givenLocked();
        given(completeMultipartUploadPort.complete("k/1/big.png", "upload-1", 3))
                .willReturn(MultipartCompletion.COMPLETED);
        given(loadRecordCardImagePort.load("k/1/big.png")).willReturn(Optional.of(new UploadedImage(BIG, "image/png")));

        // when
        handler.handle(command());

        // then
        RecordCardDraft merged = draft.withImage(0, draft.images().getFirst().merge());
        InOrder order = inOrder(completeMultipartUploadPort, updateRecordCardDraftPort, loadRecordCardImagePort,
                recordCardDraftSaver);
        order.verify(completeMultipartUploadPort).complete("k/1/big.png", "upload-1", 3);
        order.verify(updateRecordCardDraftPort).update(merged);
        order.verify(loadRecordCardImagePort).load("k/1/big.png");
        order.verify(recordCardDraftSaver).save(merged);
        order.verify(updateRecordCardDraftPort).update(merged.confirm());
    }

    @Test
    @DisplayName("이미 합친 사진은 다시 합치지 않고 HEAD만 본다")
    void skipAlreadyMerged() {
        // given
        givenDraft(draft(multipartImage(IMAGE_ID_1, "k/1/big.png", true)));
        givenLocked();
        given(loadRecordCardImagePort.load("k/1/big.png")).willReturn(Optional.of(new UploadedImage(BIG, "image/png")));

        // when
        handler.handle(command());

        // then
        verifyNoInteractions(completeMultipartUploadPort);
    }

    @Test
    @DisplayName("조각이 빠졌으면 400이고 합침 표시·저장을 하지 않는다")
    void partsMissing() {
        // given
        givenDraft(draft(multipartImage(IMAGE_ID_1, "k/1/big.png", false)));
        givenLocked();
        given(completeMultipartUploadPort.complete("k/1/big.png", "upload-1", 3))
                .willReturn(MultipartCompletion.PARTS_MISSING);

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardImageNotUploadedException.class);
        verify(updateRecordCardDraftPort, never()).update(any());
        verifyNoInteractions(recordCardDraftSaver);
        verify(lockRecordCardDraftPort).unlock(DRAFT_ID, TOKEN);
    }

    @Test
    @DisplayName("UploadId가 없어도 객체가 있으면 이미 합쳐진 것이라 이어 간다 — 합침 표시 전에 끊긴 재시도")
    void uploadGoneButObjectExists() {
        // given
        givenDraft(draft(multipartImage(IMAGE_ID_1, "k/1/big.png", false)));
        givenLocked();
        given(completeMultipartUploadPort.complete("k/1/big.png", "upload-1", 3))
                .willReturn(MultipartCompletion.UPLOAD_NOT_FOUND);
        given(loadRecordCardImagePort.load("k/1/big.png")).willReturn(Optional.of(new UploadedImage(BIG, "image/png")));

        // when & then
        assertThat(handler.handle(command()).recordCardId()).isEqualTo(CARD_ID);
    }

    @Test
    @DisplayName("UploadId도 없고 객체도 없으면 400이다")
    void uploadGoneAndNoObject() {
        // given
        givenDraft(draft(multipartImage(IMAGE_ID_1, "k/1/big.png", false)));
        givenLocked();
        given(completeMultipartUploadPort.complete("k/1/big.png", "upload-1", 3))
                .willReturn(MultipartCompletion.UPLOAD_NOT_FOUND);
        given(loadRecordCardImagePort.load("k/1/big.png")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardImageNotUploadedException.class);
        verifyNoInteractions(recordCardDraftSaver);
    }

    @Test
    @DisplayName("앞 사진을 합친 뒤 뒤 사진이 없어 실패해도 앞 사진의 '합침'은 남는다 — 다시 부르면 다시 합치지 않는다")
    void keepMergedMarkWhenLaterImageFails() {
        // given
        RecordCardDraft draft = draft(multipartImage(IMAGE_ID_1, "k/1/big.png", false),
                singleImage(IMAGE_ID_2, "k/2/b.jpg"));
        givenDraft(draft);
        givenLocked();
        given(completeMultipartUploadPort.complete("k/1/big.png", "upload-1", 3))
                .willReturn(MultipartCompletion.COMPLETED);
        given(loadRecordCardImagePort.load("k/1/big.png")).willReturn(Optional.of(new UploadedImage(BIG, "image/png")));
        given(loadRecordCardImagePort.load("k/2/b.jpg")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardImageNotUploadedException.class);
        ArgumentCaptor<RecordCardDraft> updated = ArgumentCaptor.forClass(RecordCardDraft.class);
        verify(updateRecordCardDraftPort).update(updated.capture());
        assertThat(updated.getValue().images().get(0).merged()).isTrue();
        assertThat(updated.getValue().confirmed()).isFalse();
    }

    @Test
    @DisplayName("DB 저장이 409로 끝나면 확정 표시 없이 잠금만 풀고 예외를 그대로 낸다")
    void saveFailsReleasesLock() {
        // given
        givenDraft(draft(singleImage(IMAGE_ID_1, "k/1/a.jpg")));
        givenLocked();
        given(loadRecordCardImagePort.load("k/1/a.jpg")).willReturn(Optional.of(new UploadedImage(10L, "image/jpeg")));
        willThrow(new RecordCardDraftInProgressException()).given(recordCardDraftSaver).save(any());

        // when & then
        assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(RecordCardDraftInProgressException.class);
        verify(updateRecordCardDraftPort, never()).update(any());
        verify(lockRecordCardDraftPort).unlock(DRAFT_ID, TOKEN);
    }

    @Test
    @DisplayName("확정 표시를 못 남겨도(그사이 초안 만료) 카드는 저장됐으니 정상 응답이다")
    void confirmMarkFailsStillSucceeds() {
        // given
        givenDraft(draft(singleImage(IMAGE_ID_1, "k/1/a.jpg")));
        givenLocked();
        given(loadRecordCardImagePort.load("k/1/a.jpg")).willReturn(Optional.of(new UploadedImage(10L, "image/jpeg")));
        given(updateRecordCardDraftPort.update(any())).willReturn(false);

        // when & then
        assertThat(handler.handle(command()).recordCardId()).isEqualTo(CARD_ID);
    }

    private void givenDraft(RecordCardDraft draft) {
        given(loadRecordCardDraftPort.load(DRAFT_ID)).willReturn(Optional.of(draft));
    }

    private void givenLocked() {
        given(lockRecordCardDraftPort.tryLock(DRAFT_ID, LOCK_TTL)).willReturn(Optional.of(TOKEN));
    }

    private static CompleteRecordCardDraftCommand command() {
        return new CompleteRecordCardDraftCommand(USER_ID, DRAFT_ID);
    }

    private static RecordCardDraft draft(DraftImage... images) {
        return new RecordCardDraft(DRAFT_ID, DraftType.CREATE, CARD_ID, USER_ID, 501L, RecordCardActivityType.DAILY,
                "바람이 좋았다", RecordCardVisibility.PUBLIC, List.of(images), images.length == 0 ? null : 0, false);
    }

    private static DraftImage singleImage(UUID id, String key) {
        return new DraftImage(id, key, "image/jpeg", 10L, null, UploadType.SINGLE, null, null, false);
    }

    private static DraftImage multipartImage(UUID id, String key, boolean merged) {
        return new DraftImage(id, key, "image/png", BIG, null, UploadType.MULTIPART, "upload-1", 3, merged);
    }
}
