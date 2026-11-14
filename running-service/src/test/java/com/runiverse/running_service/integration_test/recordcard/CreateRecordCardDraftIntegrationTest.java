package com.runiverse.running_service.integration_test.recordcard;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftHandler;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageTooLargeException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.integration_test.fake.FakeImageIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeMultipartUploadStarter;
import com.runiverse.running_service.integration_test.fake.FakeRecordCardIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeUploadUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryRecordCardDraftStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRecordCardStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("기록 카드 작성 요청(9-1) 통합 테스트")
class CreateRecordCardDraftIntegrationTest {

    private static final long RECORD_ID = 501L;
    private static final long TEN_MB = DataSize.ofMegabytes(10).toBytes();
    private static final Duration TTL = Duration.ofMinutes(30);

    private UUID userId;
    private InMemoryRecordCardStore recordCardStore;
    private InMemoryRecordCardDraftStore draftStore;
    private FakeRecordCardIdGenerator draftIds;
    private FakeRecordCardIdGenerator cardIds;
    private FakeImageIdGenerator imageIds;
    private FakeUploadUrlGenerator uploadUrls;
    private FakeMultipartUploadStarter multipartUploads;
    private CreateRecordCardDraftHandler handler;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        recordCardStore = new InMemoryRecordCardStore();
        draftStore = new InMemoryRecordCardDraftStore();
        draftIds = new FakeRecordCardIdGenerator(1L);
        cardIds = new FakeRecordCardIdGenerator(2L);
        imageIds = new FakeImageIdGenerator();
        uploadUrls = new FakeUploadUrlGenerator();
        multipartUploads = new FakeMultipartUploadStarter();
        handler = new CreateRecordCardDraftHandler(
                new RecordCardDraftProperties(TTL, DataSize.ofMegabytes(100), DataSize.ofMegabytes(10),
                        Duration.ofSeconds(30)),
                recordCardStore,    // ExistsOwnRunningRecordPort
                recordCardStore,    // FindRecordCardIdByRunningRecordPort
                draftIds,           // GenerateRecordCardDraftIdPort
                cardIds,            // GenerateRecordCardIdPort
                imageIds,           // GenerateImageIdPort
                uploadUrls,         // GenerateUploadUrlPort
                multipartUploads,   // StartMultipartUploadPort
                draftStore          // SaveRecordCardDraftPort
        );
        recordCardStore.addRecord(RECORD_ID, userId);
    }

    @Test
    @DisplayName("내 기록이면 작은 사진은 한 번에, 큰 사진은 조각으로 올릴 곳을 주고 초안을 남긴다")
    void createDraftWithSingleAndMultipart() {
        // when
        CreateRecordCardDraftResult result = handler.handle(command(List.of(
                new CreateRecordCardDraftCommand.Image("image/jpeg", 204_800L, "IMG_2034.jpg", "노을"),
                new CreateRecordCardDraftCommand.Image("image/png", TEN_MB * 2 + 1, "한강 야경.png", null)), null));

        // then
        UUID cardId = cardIds.idAt(1);
        String smallKey = "record-cards/%s/%s/%s/IMG_2034.jpg".formatted(userId, cardId, imageIds(1));
        String largeKey = "record-cards/%s/%s/%s/한강 야경.png".formatted(userId, cardId, imageIds(2));
        assertThat(result.recordCardDraftId()).isEqualTo(draftIds.idAt(1));
        assertThat(result.uploads()).hasSize(2);
        assertThat(result.uploads().get(0)).isEqualTo(
                new Upload.Single(imageIds(1), smallKey, uploadUrls.urlOf(smallKey)));
        Upload.Multipart large = (Upload.Multipart) result.uploads().get(1);
        assertThat(large.recordCardImageId()).isEqualTo(imageIds(2));
        assertThat(large.imageKey()).isEqualTo(largeKey);
        assertThat(large.parts()).hasSize(3);

        RecordCardDraft draft = draftStore.find(result.recordCardDraftId()).orElseThrow();
        assertThat(draftStore.ttlOf(result.recordCardDraftId())).isEqualTo(TTL);
        assertThat(draft.type()).isEqualTo(RecordCardDraft.DraftType.CREATE);
        assertThat(draft.recordCardId()).isEqualTo(cardId);
        assertThat(draft.thumbnailIndex()).isZero();
        assertThat(draft.images()).containsExactly(
                new DraftImage(imageIds(1), smallKey, "image/jpeg", 204_800L, "노을", UploadType.SINGLE,
                        null, null, false),
                new DraftImage(imageIds(2), largeKey, "image/png", TEN_MB * 2 + 1, null, UploadType.MULTIPART,
                        multipartUploads.uploadIdOf(largeKey), 3, false));
    }

    @Test
    @DisplayName("같은 기록으로 다시 요청하면 초안도 카드 ID도 새로 나온다 — 먼저 확정한 쪽만 카드가 된다")
    void requestAgainGivesNewDraft() {
        // when
        CreateRecordCardDraftResult first = handler.handle(command(List.of(), null));
        CreateRecordCardDraftResult second = handler.handle(command(List.of(), null));

        // then
        assertThat(first.recordCardDraftId()).isNotEqualTo(second.recordCardDraftId());
        assertThat(draftStore.find(first.recordCardDraftId()).orElseThrow().recordCardId())
                .isNotEqualTo(draftStore.find(second.recordCardDraftId()).orElseThrow().recordCardId());
    }

    @Test
    @DisplayName("남의 기록·숨긴 방의 기록·없는 기록이면 404이고, 업로드 URL도 초안도 남기지 않는다")
    void rejectNotOwnRecord() {
        // given
        recordCardStore.addRecord(601L, UuidCreator.getTimeOrderedEpoch());
        recordCardStore.addRecordInHiddenRoom(701L, userId);

        // when & then
        for (long recordId : new long[]{601L, 701L, 999L}) {
            assertThatThrownBy(() -> handler.handle(commandFor(recordId)))
                    .isInstanceOf(RunningRecordNotFoundException.class);
        }
        assertThat(uploadUrls.isEmpty()).isTrue();
        assertThat(draftStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("이 기록에 카드가 있으면 409이고, 업로드 URL도 초안도 남기지 않는다")
    void rejectLiveCard() {
        // given
        recordCardStore.addCard(RECORD_ID, UuidCreator.getTimeOrderedEpoch());

        // when & then
        assertThatThrownBy(() -> handler.handle(commandFor(RECORD_ID)))
                .isInstanceOf(RecordCardAlreadyExistsException.class);
        assertThat(uploadUrls.isEmpty()).isTrue();
        assertThat(draftStore.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("최대 크기를 넘는 사진이 하나라도 있으면 400이고, 조각 업로드도 시작하지 않는다")
    void rejectTooLarge() {
        // when & then
        assertThatThrownBy(() -> handler.handle(command(List.of(
                new CreateRecordCardDraftCommand.Image("image/png", TEN_MB * 2, "big.png", null),
                new CreateRecordCardDraftCommand.Image("image/png", TEN_MB * 10 + 1, "huge.png", null)), null)))
                .isInstanceOf(RecordCardImageTooLargeException.class);
        assertThat(multipartUploads.started()).isEmpty();
        assertThat(draftStore.isEmpty()).isTrue();
    }

    private UUID imageIds(int sequence) {
        return new UUID(0L, sequence);
    }

    private CreateRecordCardDraftCommand command(List<CreateRecordCardDraftCommand.Image> images,
                                                 Integer thumbnailIndex) {
        return new CreateRecordCardDraftCommand(userId, RECORD_ID, "TRAVEL", "바람이 좋았다", "PUBLIC", images,
                thumbnailIndex);
    }

    private CreateRecordCardDraftCommand commandFor(long recordId) {
        return new CreateRecordCardDraftCommand(userId, recordId, "TRAVEL", "바람이 좋았다", "PUBLIC",
                List.of(new CreateRecordCardDraftCommand.Image("image/jpeg", 1L, "a.jpg", null)), null);
    }
}
