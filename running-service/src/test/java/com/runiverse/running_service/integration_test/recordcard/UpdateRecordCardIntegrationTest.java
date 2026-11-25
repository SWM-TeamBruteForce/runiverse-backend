package com.runiverse.running_service.integration_test.recordcard;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftHandler;
import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardDraftSaver;
import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardEditDraftSaver;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardHandler;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardResult;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer;
import com.runiverse.running_service.application.recordcard.exception.RecordCardChangedException;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.integration_test.fake.FakeImageIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeMultipartUploadStarter;
import com.runiverse.running_service.integration_test.fake.FakeRecordCardIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeRecordCardObjectStore;
import com.runiverse.running_service.integration_test.fake.FakeUploadUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryRecordCardDraftStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRecordCardMusicRequests;
import com.runiverse.running_service.integration_test.fake.InMemoryRecordCardStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.runiverse.running_service.application.recordcard.common.RecordCardStorageKeys.thumbnailKeyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 9-3 수정 → (새 사진이면) 업로드 → 9-2 수정 확정을 한 흐름으로 엮는다 — 스프링 없이 페이크로 조립한다.
// 응답의 카드(상세 조회)는 8-1 통합 테스트가 본다 — 여기서는 카드·이력·음악 요청이 어떻게 남는지만 본다
@DisplayName("기록 카드 수정(9-3·9-2) 통합 테스트")
class UpdateRecordCardIntegrationTest {

    private static final long RECORD_ID = 501L;
    private static final UUID A = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012a");
    private static final UUID B = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012b");

    private UUID owner;
    private UUID cardId;
    private InMemoryRecordCardStore recordCardStore;
    private InMemoryRecordCardMusicRequests musicRequests;
    private InMemoryRecordCardDraftStore draftStore;
    private FakeRecordCardObjectStore objectStore;
    private UpdateRecordCardHandler updateHandler;
    private CompleteRecordCardDraftHandler completeHandler;

    @BeforeEach
    void setUp() {
        owner = UuidCreator.getTimeOrderedEpoch();
        recordCardStore = new InMemoryRecordCardStore();
        musicRequests = new InMemoryRecordCardMusicRequests();
        draftStore = new InMemoryRecordCardDraftStore();
        objectStore = new FakeRecordCardObjectStore();
        FakeMultipartUploadStarter multipartUploads = new FakeMultipartUploadStarter();
        RecordCardDraftProperties properties = new RecordCardDraftProperties(Duration.ofMinutes(30),
                DataSize.ofMegabytes(100), DataSize.ofMegabytes(1), DataSize.ofMegabytes(10), Duration.ofSeconds(30));
        RecordCardEditor editor = new RecordCardEditor(recordCardStore, recordCardStore, recordCardStore,
                recordCardStore, recordCardStore, musicRequests, recordCardStore);
        updateHandler = new UpdateRecordCardHandler(properties, editor,
                new RecordCardUploadIssuer(properties, new FakeImageIdGenerator(), new FakeUploadUrlGenerator(),
                        multipartUploads),
                new FakeRecordCardIdGenerator(3L), draftStore, query -> null);
        completeHandler = new CompleteRecordCardDraftHandler(properties, draftStore, draftStore, draftStore,
                objectStore, objectStore,
                new RecordCardDraftSaver(recordCardStore, recordCardStore, recordCardStore, recordCardStore,
                        recordCardStore, musicRequests),
                new RecordCardEditDraftSaver(editor));

        // 음악이 모두 끝난 카드 — 사진 A(설명 "노을")·B(설명 없음)
        recordCardStore.addRecord(RECORD_ID, owner);
        RecordCard card = RecordCard.create(UuidCreator.getTimeOrderedEpoch(), RECORD_ID, owner,
                RecordCardVisibility.PUBLIC, RecordCardActivityType.DAILY, "한강 한 바퀴", DisplayOrder.FIRST,
                List.of(new RecordCard.ImageDraft(A, "record-cards/u/c/a/a.jpg", "노을"),
                        new RecordCard.ImageDraft(B, "record-cards/u/c/b/b.jpg", null)), 0);
        card.completeBaseMusic(MusicStatus.COMPLETED, "audio/base.wav");
        card.completeImageMusic(A, MusicStatus.COMPLETED, "audio/a.wav");
        card.completeImageMusic(B, MusicStatus.COMPLETED, "audio/b.wav");
        recordCardStore.create(card);
        cardId = card.getRecordCardId().value();
    }

    @Test
    @DisplayName("순서만 바꾸면 바로 반영하고 이력을 남기지만, 음악은 요청하지 않는다")
    void orderOnly() {
        // when
        UpdateRecordCardResult result = update(null, List.of(kept(B, null), kept(A, "노을")));

        // then
        assertThat(result.draft()).isNull();
        assertThat(card().getImages()).extracting(image -> image.getRecordCardImageId().value())
                .containsExactly(B, A);
        assertThat(recordCardStore.snapshots()).extracting(InMemoryRecordCardStore.Snapshot::reason)
                .containsExactly(RecordCardSnapshotReason.UPDATE);
        assertThat(musicRequests.requests()).isEmpty();
    }

    @Test
    @DisplayName("회고 글만 바꾸면 사진 없는 요청 한 건(기본곡만)이 나가고, 사진 음악은 그대로다")
    void memoOnly() {
        // when
        update("바람이 좋았던 한강", null);

        // then
        assertThat(card().getMemo().value()).isEqualTo("바람이 좋았던 한강");
        assertThat(musicRequests.requests()).singleElement()
                .satisfies(request -> assertThat(request.photos()).isEmpty());
        assertThat(card().getImages()).extracting(RecordCardImage::getMusicStatus).containsOnly(MusicStatus.COMPLETED);
    }

    @Test
    @DisplayName("같은 값으로 다시 보내면 이력도 요청도 남기지 않는다")
    void sameValues() {
        // when
        update("한강 한 바퀴", List.of(kept(A, "노을"), kept(B, null)));

        // then
        assertThat(recordCardStore.snapshots()).isEmpty();
        assertThat(recordCardStore.updateCount()).isZero();
        assertThat(musicRequests.requests()).isEmpty();
    }

    @Test
    @DisplayName("새 사진은 초안만 만들고 카드는 그대로다 — 올리고 9-2로 확정하면 반영되고, 새 사진·설명이 바뀐 사진만 요청한다")
    void newImageThroughConfirm() {
        // when — A 설명 변경, B 빼기, 새 사진 N 추가
        UpdateRecordCardResult result = update(null, List.of(kept(A, "다리 밑 노을"),
                new UpdateRecordCardCommand.Image(null, "image/jpeg", 1_000L, 100L, "n.jpg", "결승선")));

        // then — 확정 전: 카드·이력·요청 모두 그대로
        assertThat(result.draft()).isNotNull();
        assertThat(card().getImages()).hasSize(2);
        assertThat(recordCardStore.snapshots()).isEmpty();
        assertThat(musicRequests.requests()).isEmpty();

        // when — 앱이 올리고 확정한다
        Upload upload = result.draft().uploads().getFirst();
        objectStore.putObject(upload.imageKey(), 1_000L, "image/jpeg");
        objectStore.putObject(thumbnailKeyOf(upload.imageKey()), 100L, "image/jpeg");
        UUID confirmed = completeHandler.handle(
                new CompleteRecordCardDraftCommand(owner, result.draft().recordCardDraftId())).recordCardId();

        // then — 같은 카드가 고쳐졌다
        assertThat(confirmed).isEqualTo(cardId);
        RecordCard card = card();
        assertThat(card.getImages()).extracting(image -> image.getRecordCardImageId().value(),
                        image -> image.getCaption().map(RecordCardImageCaption::value).orElse(null),
                        RecordCardImage::getMusicStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(A, "다리 밑 노을", MusicStatus.GENERATING),
                        org.assertj.core.groups.Tuple.tuple(upload.recordCardImageId(), "결승선",
                                MusicStatus.GENERATING));
        // 기본곡 상태는 되돌리지 않는다 — 결과가 오면 받는 쪽이 따른다
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(recordCardStore.snapshots()).hasSize(1);
        assertThat(musicRequests.requests()).singleElement().satisfies(request -> assertThat(request.photos())
                .extracting(Photo::recordCardImageId).containsExactly(A, upload.recordCardImageId()));
    }

    @Test
    @DisplayName("수정 초안을 만든 뒤 다른 수정이 반영되면 확정은 409이고, 그 수정은 그대로 남는다")
    void changedBeforeConfirm() {
        // given — 새 사진 초안을 만들어 두고
        UpdateRecordCardResult draft = update(null, List.of(kept(A, "노을"), kept(B, null),
                new UpdateRecordCardCommand.Image(null, "image/jpeg", 1_000L, 100L, "n.jpg", null)));
        objectStore.putObject(draft.draft().uploads().getFirst().imageKey(), 1_000L, "image/jpeg");
        objectStore.putObject(thumbnailKeyOf(draft.draft().uploads().getFirst().imageKey()), 100L, "image/jpeg");
        // 다른 기기에서 회고 글을 바로 고친다
        update("다른 기기에서 고친 회고", null);

        // when & then
        assertThatThrownBy(() -> completeHandler.handle(
                new CompleteRecordCardDraftCommand(owner, draft.draft().recordCardDraftId())))
                .isInstanceOf(RecordCardChangedException.class);
        assertThat(card().getMemo().value()).isEqualTo("다른 기기에서 고친 회고");
        assertThat(card().getImages()).hasSize(2);
    }

    private UpdateRecordCardResult update(String memo, List<UpdateRecordCardCommand.Image> images) {
        return updateHandler.handle(new UpdateRecordCardCommand(owner, cardId, memo, null, null, images, null));
    }

    private RecordCard card() {
        return recordCardStore.cardOf(RECORD_ID).orElseThrow();
    }

    private static UpdateRecordCardCommand.Image kept(UUID id, String caption) {
        return new UpdateRecordCardCommand.Image(id, null, null, null, null, caption);
    }
}
