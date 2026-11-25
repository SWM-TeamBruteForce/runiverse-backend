package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor.Changes;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardCommand.Image;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardHandler;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardResult;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer.Issued;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer.NewImage;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardDetailUsecase;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardDraftIdPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftType;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.EditPlan;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;
import com.runiverse.running_service.domain.recordcard.RecordCard.ImageSlot;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 수정 단위 테스트")
class UpdateRecordCardHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CARD_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID DRAFT_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID KEPT = UuidCreator.getTimeOrderedEpoch();
    private static final UUID ISSUED = UuidCreator.getTimeOrderedEpoch();
    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock
    private RecordCardEditor recordCardEditor;
    @Mock
    private RecordCardUploadIssuer recordCardUploadIssuer;
    @Mock
    private GenerateRecordCardDraftIdPort generateRecordCardDraftIdPort;
    @Mock
    private SaveRecordCardDraftPort saveRecordCardDraftPort;
    @Mock
    private GetRecordCardDetailUsecase getRecordCardDetailUsecase;

    private final GetRecordCardDetailResult detail = mock(GetRecordCardDetailResult.class);
    private UpdateRecordCardHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateRecordCardHandler(new RecordCardDraftProperties(TTL, DataSize.ofMegabytes(100), DataSize.ofMegabytes(1),
                DataSize.ofMegabytes(10), Duration.ofSeconds(30)), recordCardEditor, recordCardUploadIssuer,
                generateRecordCardDraftIdPort, saveRecordCardDraftPort, getRecordCardDetailUsecase);
    }

    @Test
    @DisplayName("새 사진이 없으면 바로 반영하고, 반영된 카드를 상세 조회로 만들어 draft 없이 돌려준다")
    void appliesNow() {
        // given
        when(getRecordCardDetailUsecase.handle(new GetRecordCardDetailQuery(USER_ID, CARD_ID))).thenReturn(detail);

        // when
        UpdateRecordCardResult result = handler.handle(command("새 회고", "RACE", "PRIVATE",
                List.of(kept(KEPT, "노을")), 0));

        // then
        InOrder order = inOrder(recordCardEditor, getRecordCardDetailUsecase);
        order.verify(recordCardEditor).applyNow(USER_ID, CARD_ID, new Changes("새 회고", RecordCardActivityType.RACE,
                RecordCardVisibility.PRIVATE, List.of(new ImageSlot.Keep(KEPT, "노을")), 0));
        order.verify(getRecordCardDetailUsecase).handle(new GetRecordCardDetailQuery(USER_ID, CARD_ID));
        assertThat(result.recordCard()).isSameAs(detail);
        assertThat(result.draft()).isNull();
        verifyNoInteractions(recordCardUploadIssuer, saveRecordCardDraftPort);
    }

    @Test
    @DisplayName("사진을 생략하면 사진·설명은 그대로 두라고 넘긴다(자리 없음)")
    void imagesOmitted() {
        // given
        when(getRecordCardDetailUsecase.handle(any())).thenReturn(detail);

        // when
        handler.handle(command("새 회고", null, null, null, null));

        // then
        verify(recordCardEditor).applyNow(USER_ID, CARD_ID, new Changes("새 회고", null, null, null, null));
    }

    @Test
    @DisplayName("새 사진이 있으면 반영하지 않고, 확인을 먼저 한 뒤 새 사진에만 업로드 URL을 주는 수정 초안을 만든다")
    void createsEditDraft() {
        // given
        when(recordCardEditor.checkBeforeDraft(USER_ID, CARD_ID, List.of(new ImageSlot.Keep(KEPT, "노을"))))
                .thenReturn(Optional.of(7L));
        DraftImage draftImage = new DraftImage(ISSUED, "record-cards/u/c/i/IMG_3001.jpg", "image/jpeg", 1024L,
                "결승선", UploadType.SINGLE, null, null, false, 100L);
        Upload upload = new Upload.Single(ISSUED, draftImage.imageKey(), "https://upload",
                new Upload.Thumbnail("record-cards/u/c/i/thumbnail/IMG_3001.jpg", "https://thumb"));
        NewImage newImage = new NewImage("image/jpeg", 1024L, 100L, "IMG_3001.jpg", "결승선");
        when(recordCardUploadIssuer.issue(USER_ID, CARD_ID, newImage)).thenReturn(new Issued(draftImage, upload));
        when(generateRecordCardDraftIdPort.generate()).thenReturn(DRAFT_ID);
        when(getRecordCardDetailUsecase.handle(new GetRecordCardDetailQuery(USER_ID, CARD_ID))).thenReturn(detail);

        // when — 새 사진을 앞에, 남길 사진을 뒤에
        UpdateRecordCardResult result = handler.handle(command("새 회고", null, null,
                List.of(added("결승선"), kept(KEPT, "노을")), 0));

        // then — 크기·카드 확인이 발급보다 먼저다
        InOrder order = inOrder(recordCardUploadIssuer, recordCardEditor);
        order.verify(recordCardUploadIssuer).rejectTooLarge(List.of(newImage));
        order.verify(recordCardEditor).checkBeforeDraft(any(), any(), any());
        order.verify(recordCardUploadIssuer).issue(USER_ID, CARD_ID, newImage);
        verify(recordCardEditor, never()).applyNow(any(), any(), any());

        ArgumentCaptor<RecordCardDraft> saved = ArgumentCaptor.forClass(RecordCardDraft.class);
        verify(saveRecordCardDraftPort).save(saved.capture(), eq(TTL));
        RecordCardDraft draft = saved.getValue();
        assertThat(draft.type()).isEqualTo(DraftType.UPDATE);
        assertThat(draft.recordCardDraftId()).isEqualTo(DRAFT_ID);
        assertThat(draft.recordCardId()).isEqualTo(CARD_ID);
        assertThat(draft.userId()).isEqualTo(USER_ID);
        assertThat(draft.images()).containsExactly(draftImage);
        assertThat(draft.editPlan()).isEqualTo(new EditPlan("새 회고", null, null, List.of(
                new EditPlan.Slot(ISSUED, "결승선", true), new EditPlan.Slot(KEPT, "노을", false)), 0, 7L));

        assertThat(result.recordCard()).isSameAs(detail);
        assertThat(result.draft().recordCardDraftId()).isEqualTo(DRAFT_ID);
        assertThat(result.draft().uploads()).containsExactly(upload);
    }

    @Test
    @DisplayName("초안 전 확인에서 막히면(남의 카드 등) 업로드 URL도 초안도 만들지 않는다")
    void stopsBeforeIssuing() {
        // given
        when(recordCardEditor.checkBeforeDraft(any(), any(), any())).thenThrow(new RecordCardNotFoundException());

        // when & then
        assertThatThrownBy(() -> handler.handle(command(null, null, null, List.of(added(null)), null)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verify(recordCardUploadIssuer, never()).issue(any(), any(), any());
        verifyNoInteractions(saveRecordCardDraftPort, getRecordCardDetailUsecase);
    }

    private static UpdateRecordCardCommand command(String memo, String activityType, String visibility,
                                                   List<Image> images, Integer thumbnailIndex) {
        return new UpdateRecordCardCommand(USER_ID, CARD_ID, memo, activityType, visibility, images, thumbnailIndex);
    }

    private static Image kept(UUID id, String caption) {
        return new Image(id, null, null, null, null, caption);
    }

    private static Image added(String caption) {
        return new Image(null, "image/jpeg", 1024L, 100L, " IMG_3001.jpg ", caption);
    }
}
