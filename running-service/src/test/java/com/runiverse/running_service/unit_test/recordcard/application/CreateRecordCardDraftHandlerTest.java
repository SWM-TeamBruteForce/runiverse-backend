package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.common.port.out.GenerateImageIdPort;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftHandler;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageTooLargeException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.ExistsOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardDraftIdPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardIdPort;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardUploadUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;
import com.runiverse.running_service.application.recordcard.port.out.StartMultipartUploadPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 작성 요청(9-1) 단위 테스트")
class CreateRecordCardDraftHandlerTest {

    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
    private static final UUID DRAFT_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400d1");
    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID IMAGE_ID_1 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final UUID IMAGE_ID_2 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e");
    private static final long RUNNING_RECORD_ID = 501L;
    private static final String MEMO = "한강 야간 러닝, 바람이 좋았다";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final long PART_SIZE = DataSize.ofMegabytes(10).toBytes();
    private static final long MAX_SIZE = DataSize.ofMegabytes(100).toBytes();

    @Mock
    private ExistsOwnRunningRecordPort existsOwnRunningRecordPort;
    @Mock
    private FindRecordCardIdByRunningRecordPort findRecordCardIdByRunningRecordPort;
    @Mock
    private GenerateRecordCardDraftIdPort generateRecordCardDraftIdPort;
    @Mock
    private GenerateRecordCardIdPort generateRecordCardIdPort;
    @Mock
    private GenerateImageIdPort generateImageIdPort;
    @Mock
    private GenerateRecordCardUploadUrlPort generateUploadUrlPort;
    @Mock
    private StartMultipartUploadPort startMultipartUploadPort;
    @Mock
    private SaveRecordCardDraftPort saveRecordCardDraftPort;

    private CreateRecordCardDraftHandler handler;

    @BeforeEach
    void setUp() {
        RecordCardDraftProperties properties = new RecordCardDraftProperties(
                TTL, DataSize.ofMegabytes(100), DataSize.ofMegabytes(10), Duration.ofSeconds(30));
        handler = new CreateRecordCardDraftHandler(properties, existsOwnRunningRecordPort,
                findRecordCardIdByRunningRecordPort, generateRecordCardDraftIdPort, generateRecordCardIdPort,
                generateImageIdPort, generateUploadUrlPort, startMultipartUploadPort, saveRecordCardDraftPort);
    }

    @Test
    @DisplayName("10MB 이하 사진은 카드·사진 폴더 아래 올린 파일 이름 그대로의 key와 한 번에 올릴 URL을 주고, 작성 내용을 초안에 저장한다")
    void issueSingleUpload() {
        // given
        givenOwnRecordWithoutCard();
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID_1);
        String key = "record-cards/%s/%s/%s/노을 사진.JPG".formatted(USER_ID, CARD_ID, IMAGE_ID_1);
        when(generateUploadUrlPort.generate(key, "image/jpeg", 204800L, TTL)).thenReturn("https://signed");

        // when
        CreateRecordCardDraftResult result = handler.handle(command(
                List.of(new CreateRecordCardDraftCommand.Image("IMAGE/JPEG", 204800L, "노을 사진.JPG", "다리 밑 노을")), null));

        // then
        assertThat(result.recordCardDraftId()).isEqualTo(DRAFT_ID);
        assertThat(result.uploads()).containsExactly(new Upload.Single(IMAGE_ID_1, key, "https://signed"));

        RecordCardDraft draft = savedDraft();
        assertThat(draft.recordCardDraftId()).isEqualTo(DRAFT_ID);
        assertThat(draft.type()).isEqualTo(RecordCardDraft.DraftType.CREATE);
        assertThat(draft.recordCardId()).isEqualTo(CARD_ID);
        assertThat(draft.userId()).isEqualTo(USER_ID);
        assertThat(draft.runningRecordId()).isEqualTo(RUNNING_RECORD_ID);
        assertThat(draft.activityType()).isEqualTo(RecordCardActivityType.DAILY);
        assertThat(draft.visibility()).isEqualTo(RecordCardVisibility.PUBLIC);
        assertThat(draft.memo()).isEqualTo(MEMO);
        assertThat(draft.thumbnailIndex()).isZero();
        assertThat(draft.confirmed()).isFalse();
        assertThat(draft.images()).containsExactly(new DraftImage(IMAGE_ID_1, key, "image/jpeg", 204800L,
                "다리 밑 노을", UploadType.SINGLE, null, null, false));
    }

    @Test
    @DisplayName("10MB를 넘는 사진은 조각 URL을 주고, S3 UploadId는 초안에만 둔다")
    void issueMultipartUpload() {
        // given
        givenOwnRecordWithoutCard();
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID_1);
        long size = PART_SIZE * 2 + 1;
        String key = "record-cards/%s/%s/%s/IMG_0002.png".formatted(USER_ID, CARD_ID, IMAGE_ID_1);
        List<MultipartUpload.Part> parts = List.of(
                new MultipartUpload.Part(1, "https://p1"),
                new MultipartUpload.Part(2, "https://p2"),
                new MultipartUpload.Part(3, "https://p3"));
        when(startMultipartUploadPort.start(key, "image/png", size, PART_SIZE, TTL))
                .thenReturn(new MultipartUpload("s3-upload-id", parts));

        // when
        CreateRecordCardDraftResult result = handler.handle(command(
                List.of(new CreateRecordCardDraftCommand.Image("image/png", size, "IMG_0002.png", null)), null));

        // then
        assertThat(result.uploads()).containsExactly(new Upload.Multipart(IMAGE_ID_1, key, PART_SIZE, parts));
        assertThat(savedDraft().images()).containsExactly(new DraftImage(IMAGE_ID_1, key, "image/png", size,
                null, UploadType.MULTIPART, "s3-upload-id", 3, false));
        verify(generateUploadUrlPort, never()).generate(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("딱 10MB면 나누지 않고 한 번에 올린다")
    void partSizeBoundaryIsSingle() {
        // given
        givenOwnRecordWithoutCard();
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID_1);

        // when
        CreateRecordCardDraftResult result = handler.handle(command(
                List.of(new CreateRecordCardDraftCommand.Image("image/webp", PART_SIZE, "run.webp", null)), null));

        // then
        assertThat(result.uploads().getFirst()).isInstanceOf(Upload.Single.class);
        verifyNoInteractions(startMultipartUploadPort);
    }

    @Test
    @DisplayName("사진이 여러 장이면 요청 순서대로 주고, 고른 썸네일 위치를 담는다")
    void keepOrderAndThumbnail() {
        // given
        givenOwnRecordWithoutCard();
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID_1, IMAGE_ID_2);

        // when
        CreateRecordCardDraftResult result = handler.handle(command(List.of(
                new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "IMG_0001.jpg", null),
                new CreateRecordCardDraftCommand.Image("image/png", 2L, "IMG_0002.png", null)), 1));

        // then
        assertThat(result.uploads()).extracting(Upload::imageKey).containsExactly(
                "record-cards/%s/%s/%s/IMG_0001.jpg".formatted(USER_ID, CARD_ID, IMAGE_ID_1),
                "record-cards/%s/%s/%s/IMG_0002.png".formatted(USER_ID, CARD_ID, IMAGE_ID_2));
        assertThat(result.uploads()).extracting(Upload::recordCardImageId).containsExactly(IMAGE_ID_1, IMAGE_ID_2);
        RecordCardDraft draft = savedDraft();
        assertThat(draft.images()).extracting(DraftImage::recordCardImageId).containsExactly(IMAGE_ID_1, IMAGE_ID_2);
        assertThat(draft.thumbnailIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 파일 이름의 사진 두 장도 사진 폴더가 달라 key가 겹치지 않는다")
    void sameFileNameDifferentKeys() {
        // given
        givenOwnRecordWithoutCard();
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID_1, IMAGE_ID_2);

        // when
        CreateRecordCardDraftResult result = handler.handle(command(List.of(
                new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "IMG_0001.jpg", null),
                new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "IMG_0001.jpg", null)), null));

        // then
        assertThat(result.uploads()).extracting(Upload::imageKey).containsExactly(
                "record-cards/%s/%s/%s/IMG_0001.jpg".formatted(USER_ID, CARD_ID, IMAGE_ID_1),
                "record-cards/%s/%s/%s/IMG_0001.jpg".formatted(USER_ID, CARD_ID, IMAGE_ID_2));
    }

    @Test
    @DisplayName("사진이 없으면 업로드 목록이 비고 썸네일도 없다")
    void noImages() {
        // given
        givenOwnRecordWithoutCard();

        // when
        CreateRecordCardDraftResult result = handler.handle(command(List.of(), null));

        // then
        assertThat(result.uploads()).isEmpty();
        RecordCardDraft draft = savedDraft();
        assertThat(draft.images()).isEmpty();
        assertThat(draft.thumbnailIndex()).isNull();
        verifyNoInteractions(generateImageIdPort, generateUploadUrlPort, startMultipartUploadPort);
    }

    @Test
    @DisplayName("공백뿐인 사진 설명은 설명 없음으로 담는다")
    void blankCaptionBecomesNull() {
        // given
        givenOwnRecordWithoutCard();
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID_1);

        // when
        handler.handle(command(List.of(new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "IMG_0001.jpg", "   ")), null));

        // then
        assertThat(savedDraft().images().getFirst().caption()).isNull();
    }

    @Test
    @DisplayName("초안 유효 시간을 저장소에 넘기고, 응답의 만료 시각은 지금부터 그 시간 뒤다")
    void passTtlAndExpiresAt() {
        // given
        givenOwnRecordWithoutCard();

        // when
        CreateRecordCardDraftResult result = handler.handle(command(List.of(), null));

        // then
        verify(saveRecordCardDraftPort).save(any(), eq(TTL));
        assertThat(result.expiresAt()).isCloseTo(LocalDateTime.now().plus(TTL), within(Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("사진이 최대 크기를 넘으면 400으로 거절하고, 기록을 읽거나 업로드를 시작하지 않는다")
    void rejectTooLargeImage() {
        // when & then -> 문구의 크기는 운영값으로 만든다
        assertThatThrownBy(() -> handler.handle(command(List.of(
                new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "IMG_0001.jpg", null),
                new CreateRecordCardDraftCommand.Image("image/jpeg", MAX_SIZE + 1, "IMG_0001.jpg", null)), null)))
                .isInstanceOf(RecordCardImageTooLargeException.class)
                .hasMessage("이미지는 100MB 이하만 업로드할 수 있습니다.");
        verifyNoInteractions(existsOwnRunningRecordPort, findRecordCardIdByRunningRecordPort,
                generateImageIdPort, generateUploadUrlPort, startMultipartUploadPort, saveRecordCardDraftPort);
    }

    @Test
    @DisplayName("딱 최대 크기인 사진은 받는다")
    void acceptMaxSizeImage() {
        // given
        givenOwnRecordWithoutCard();
        when(generateImageIdPort.generate()).thenReturn(IMAGE_ID_1);
        when(startMultipartUploadPort.start(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn(new MultipartUpload("id", List.of(new MultipartUpload.Part(1, "https://p1"))));

        // when
        CreateRecordCardDraftResult result = handler.handle(command(
                List.of(new CreateRecordCardDraftCommand.Image("image/jpeg", MAX_SIZE, "IMG_0001.jpg", null)), null));

        // then
        assertThat(result.uploads()).hasSize(1);
    }

    @Test
    @DisplayName("카드를 붙일 수 없는 기록이면 404로 거절하고, 카드를 찾거나 업로드를 시작하지 않는다")
    void rejectNotOwnRecord() {
        // given -> 없거나·남의 것이거나·숨긴 방의 기록
        when(existsOwnRunningRecordPort.existsOwnedBy(new RunningRecordId(RUNNING_RECORD_ID), new UserId(USER_ID)))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> handler.handle(command(
                List.of(new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "IMG_0001.jpg", null)), null)))
                .isInstanceOf(RunningRecordNotFoundException.class);
        verifyNoInteractions(findRecordCardIdByRunningRecordPort, generateImageIdPort, generateUploadUrlPort,
                startMultipartUploadPort, saveRecordCardDraftPort);
    }

    @Test
    @DisplayName("이 기록에 카드가 이미 있으면 409로 거절하고, 카드 ID를 만들거나 업로드를 시작하지 않는다")
    void rejectLiveCard() {
        // given
        when(existsOwnRunningRecordPort.existsOwnedBy(new RunningRecordId(RUNNING_RECORD_ID), new UserId(USER_ID)))
                .thenReturn(true);
        when(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(new RunningRecordId(RUNNING_RECORD_ID)))
                .thenReturn(Optional.of(new RecordCardId(CARD_ID)));

        // when & then
        assertThatThrownBy(() -> handler.handle(command(
                List.of(new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "IMG_0001.jpg", null)), null)))
                .isInstanceOf(RecordCardAlreadyExistsException.class);
        verifyNoInteractions(generateRecordCardIdPort, generateImageIdPort, generateUploadUrlPort,
                startMultipartUploadPort, saveRecordCardDraftPort);
    }

    private void givenOwnRecordWithoutCard() {
        when(existsOwnRunningRecordPort.existsOwnedBy(new RunningRecordId(RUNNING_RECORD_ID), new UserId(USER_ID)))
                .thenReturn(true);
        when(findRecordCardIdByRunningRecordPort.findIdByRunningRecord(new RunningRecordId(RUNNING_RECORD_ID)))
                .thenReturn(Optional.empty());
        when(generateRecordCardIdPort.generate()).thenReturn(CARD_ID);
        when(generateRecordCardDraftIdPort.generate()).thenReturn(DRAFT_ID);
    }

    private RecordCardDraft savedDraft() {
        ArgumentCaptor<RecordCardDraft> captor = ArgumentCaptor.forClass(RecordCardDraft.class);
        verify(saveRecordCardDraftPort).save(captor.capture(), eq(TTL));
        return captor.getValue();
    }

    private static CreateRecordCardDraftCommand command(List<CreateRecordCardDraftCommand.Image> images,
                                                        Integer thumbnailIndex) {
        return new CreateRecordCardDraftCommand(USER_ID, RUNNING_RECORD_ID, "DAILY", MEMO, "PUBLIC",
                images, thumbnailIndex);
    }
}
