package com.runiverse.running_service.integration_test.recordcard;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftHandler;
import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardDraftSaver;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftHandler;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftInProgressException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageNotUploadedException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.integration_test.fake.FakeImageIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeMultipartUploadStarter;
import com.runiverse.running_service.integration_test.fake.FakeRecordCardIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeRecordCardObjectStore;
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

// 9-1 작성 요청 → (앱의 업로드를 흉내) → 9-2 확정을 한 흐름으로 엮는다 — 스프링 없이 페이크로 조립한다.
// 실제 잠금·제약 이름·지연 UNIQUE는 E2E가 확인한다
@DisplayName("기록 카드 작성·확정(9-1·9-2) 통합 테스트")
class CompleteRecordCardDraftIntegrationTest {

    private static final long RECORD_ID = 501L;
    private static final long TEN_MB = DataSize.ofMegabytes(10).toBytes();

    private UUID userId;
    private InMemoryRecordCardStore recordCardStore;
    private InMemoryRecordCardDraftStore draftStore;
    private FakeRecordCardObjectStore objectStore;
    private FakeMultipartUploadStarter multipartUploads;
    private CreateRecordCardDraftHandler createHandler;
    private CompleteRecordCardDraftHandler completeHandler;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        recordCardStore = new InMemoryRecordCardStore();
        draftStore = new InMemoryRecordCardDraftStore();
        objectStore = new FakeRecordCardObjectStore();
        multipartUploads = new FakeMultipartUploadStarter();
        RecordCardDraftProperties properties = new RecordCardDraftProperties(Duration.ofMinutes(30),
                DataSize.ofMegabytes(100), DataSize.ofMegabytes(10), Duration.ofSeconds(30));
        createHandler = new CreateRecordCardDraftHandler(properties, recordCardStore, recordCardStore,
                new FakeRecordCardIdGenerator(1L), new FakeRecordCardIdGenerator(2L), new FakeImageIdGenerator(),
                new FakeUploadUrlGenerator(), multipartUploads, draftStore);
        completeHandler = new CompleteRecordCardDraftHandler(properties, draftStore, draftStore, draftStore,
                objectStore, objectStore,
                new RecordCardDraftSaver(recordCardStore, recordCardStore, recordCardStore, recordCardStore));
        recordCardStore.addRecord(RECORD_ID, userId);
    }

    @Test
    @DisplayName("작은 사진은 한 번에, 큰 사진은 조각으로 올린 뒤 확정하면 초안 그대로 카드가 생기고 음악은 생성 중이다")
    void createUploadAndComplete() {
        // given
        CreateRecordCardDraftResult draft = createDraft(RECORD_ID, List.of(
                new CreateRecordCardDraftCommand.Image("image/jpeg", 1_000L, "IMG_1.jpg", "노을"),
                new CreateRecordCardDraftCommand.Image("image/png", TEN_MB * 2 + 1, "한강 야경.png", null)));
        uploadAll(draft);

        // when
        UUID recordCardId = complete(draft.recordCardDraftId());

        // then
        RecordCard card = recordCardStore.cardOf(RECORD_ID).orElseThrow();
        assertThat(card.getRecordCardId().value()).isEqualTo(recordCardId);
        assertThat(card.getDisplayOrder()).isEqualTo(DisplayOrder.FIRST);
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        assertThat(card.getImages()).extracting(image -> image.getRecordCardImageId().value(),
                        image -> image.getImageKey().value(), RecordCardImage::getMusicStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(draft.uploads().get(0).recordCardImageId(),
                                draft.uploads().get(0).imageKey(), MusicStatus.GENERATING),
                        org.assertj.core.groups.Tuple.tuple(draft.uploads().get(1).recordCardImageId(),
                                draft.uploads().get(1).imageKey(), MusicStatus.GENERATING));
        assertThat(objectStore.hasObject(draft.uploads().get(1).imageKey())).isTrue();
        assertThat(draftStore.find(draft.recordCardDraftId()).orElseThrow().confirmed()).isTrue();
        assertThat(draftStore.isLocked(draft.recordCardDraftId())).isFalse();
    }

    @Test
    @DisplayName("같은 초안으로 다시 확정하면 두 번째 카드 없이 같은 카드 ID를 받는다")
    void retryReturnsSameCard() {
        // given
        CreateRecordCardDraftResult draft = createDraft(RECORD_ID, List.of());
        UUID first = complete(draft.recordCardDraftId());

        // when
        UUID second = complete(draft.recordCardDraftId());

        // then
        assertThat(second).isEqualTo(first);
        assertThat(recordCardStore.cardCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사진을 덜 올리고 확정하면 400이고 카드는 없다 — 빠진 것을 올리고 다시 확정하면 된다")
    void missingImageThenRetry() {
        // given
        CreateRecordCardDraftResult draft = createDraft(RECORD_ID, List.of(
                new CreateRecordCardDraftCommand.Image("image/png", TEN_MB * 2 + 1, "big.png", null),
                new CreateRecordCardDraftCommand.Image("image/jpeg", 1_000L, "small.jpg", null)));
        upload(draft.uploads().get(0), "image/png", TEN_MB * 2 + 1);

        // when & then -> 두 번째 사진이 없다. 첫 사진은 이미 합쳐진다
        assertThatThrownBy(() -> complete(draft.recordCardDraftId()))
                .isInstanceOf(RecordCardImageNotUploadedException.class);
        assertThat(recordCardStore.cardCount()).isZero();
        assertThat(draftStore.find(draft.recordCardDraftId()).orElseThrow().images().get(0).merged()).isTrue();
        assertThat(draftStore.isLocked(draft.recordCardDraftId())).isFalse();

        // when -> 빠진 사진을 올리고 다시 확정한다(첫 사진은 다시 합치지 않는다)
        upload(draft.uploads().get(1), "image/jpeg", 1_000L);
        UUID recordCardId = complete(draft.recordCardDraftId());

        // then
        assertThat(recordCardStore.cardOf(RECORD_ID).orElseThrow().getRecordCardId().value()).isEqualTo(recordCardId);
    }

    @Test
    @DisplayName("같은 기록으로 만든 다른 초안이 먼저 확정했으면 나중 확정은 409다")
    void otherDraftCompletedFirst() {
        // given
        CreateRecordCardDraftResult first = createDraft(RECORD_ID, List.of());
        CreateRecordCardDraftResult second = createDraft(RECORD_ID, List.of());
        complete(first.recordCardDraftId());

        // when & then
        assertThatThrownBy(() -> complete(second.recordCardDraftId()))
                .isInstanceOf(RecordCardAlreadyExistsException.class);
        assertThat(recordCardStore.cardCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 초안의 확정이 이미 처리 중이면 409이고 카드를 만들지 않는다")
    void lockedDraft() {
        // given
        CreateRecordCardDraftResult draft = createDraft(RECORD_ID, List.of());
        draftStore.holdLock(draft.recordCardDraftId());

        // when & then
        assertThatThrownBy(() -> complete(draft.recordCardDraftId()))
                .isInstanceOf(RecordCardDraftInProgressException.class);
        assertThat(recordCardStore.cardCount()).isZero();
    }

    @Test
    @DisplayName("다른 기록과 순위가 겹치면 409이고, 초안은 확정 전 그대로라 다시 부르면 확정된다")
    void displayOrderConflictThenRetry() {
        // given -> 다른 기록으로 카드 하나(순위 1)가 있는데, 이번 확정은 그 카드를 못 본 옛 최댓값을 읽어 순위 1을 고른다
        recordCardStore.addRecord(502L, userId);
        complete(createDraft(502L, List.of()).recordCardDraftId());
        CreateRecordCardDraftResult draft = createDraft(RECORD_ID, List.of());
        recordCardStore.readStaleMaxOnce();

        // when & then
        assertThatThrownBy(() -> complete(draft.recordCardDraftId()))
                .isInstanceOf(RecordCardDraftInProgressException.class);
        assertThat(draftStore.find(draft.recordCardDraftId()).orElseThrow().confirmed()).isFalse();

        // when -> 다시 부르면 순위를 다시 읽는다
        complete(draft.recordCardDraftId());

        // then
        assertThat(recordCardStore.cardOf(RECORD_ID).orElseThrow().getDisplayOrder()).isEqualTo(new DisplayOrder(2));
    }

    @Test
    @DisplayName("작성 요청 뒤에 방이 숨겨지면 확정은 404다")
    void roomHiddenAfterDraft() {
        // given
        CreateRecordCardDraftResult draft = createDraft(RECORD_ID, List.of());
        recordCardStore.hideRoomOf(RECORD_ID);

        // when & then
        assertThatThrownBy(() -> complete(draft.recordCardDraftId()))
                .isInstanceOf(RunningRecordNotFoundException.class);
        assertThat(draftStore.isLocked(draft.recordCardDraftId())).isFalse();
    }

    @Test
    @DisplayName("남의 초안이나 만료된 초안은 404다")
    void otherUsersOrExpiredDraft() {
        // given
        CreateRecordCardDraftResult draft = createDraft(RECORD_ID, List.of());

        // when & then
        assertThatThrownBy(() -> completeHandler.handle(
                new CompleteRecordCardDraftCommand(UuidCreator.getTimeOrderedEpoch(), draft.recordCardDraftId())))
                .isInstanceOf(RecordCardDraftNotFoundException.class);

        // given
        draftStore.expire(draft.recordCardDraftId());

        // when & then
        assertThatThrownBy(() -> complete(draft.recordCardDraftId()))
                .isInstanceOf(RecordCardDraftNotFoundException.class);
    }

    private CreateRecordCardDraftResult createDraft(long recordId, List<CreateRecordCardDraftCommand.Image> images) {
        return createHandler.handle(new CreateRecordCardDraftCommand(userId, recordId, "DAILY", "바람이 좋았다",
                "PUBLIC", images, null));
    }

    private UUID complete(UUID recordCardDraftId) {
        return completeHandler.handle(new CompleteRecordCardDraftCommand(userId, recordCardDraftId)).recordCardId();
    }

    private void uploadAll(CreateRecordCardDraftResult draft) {
        upload(draft.uploads().get(0), "image/jpeg", 1_000L);
        upload(draft.uploads().get(1), "image/png", TEN_MB * 2 + 1);
    }

    // 앱의 업로드를 흉내 낸다 — 한 번에 올린 사진은 객체가 생기고, 나눠 올린 사진은 조각이 모두 올라간다
    private void upload(Upload upload, String contentType, long size) {
        switch (upload) {
            case Upload.Single single -> objectStore.putObject(single.imageKey(), size, contentType);
            case Upload.Multipart multipart -> multipart.parts().forEach(part -> objectStore.uploadPart(
                    multipartUploads.uploadIdOf(multipart.imageKey()), multipart.imageKey(), contentType, size,
                    part.partNumber()));
        }
    }
}
