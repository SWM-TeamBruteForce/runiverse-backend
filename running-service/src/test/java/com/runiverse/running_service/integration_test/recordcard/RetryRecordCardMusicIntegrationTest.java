package com.runiverse.running_service.integration_test.recordcard;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftHandler;
import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardDraftSaver;
import com.runiverse.running_service.application.recordcard.command.completedraft.RecordCardEditDraftSaver;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicHandler;
import com.runiverse.running_service.application.recordcard.command.update.RecordCardEditor;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardHandler;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardResult;
import com.runiverse.running_service.application.recordcard.common.RecordCardDraftProperties;
import com.runiverse.running_service.application.recordcard.common.RecordCardUploadIssuer;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
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

// 9-6 음악 다시 만들기 — 잠금·되돌림·저장·요청을 실제로 엮고, 진행 중인 수정 초안(9-3 → 9-2)과 겹쳐 본다.
// 스프링 없이 페이크로 조립한다
@DisplayName("기록 카드 음악 다시 만들기(9-6) 통합 테스트")
class RetryRecordCardMusicIntegrationTest {

    private static final long RECORD_ID = 501L;
    private static final UUID A = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012a");
    private static final UUID B = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012b");

    private UUID owner;
    private UUID cardId;
    private InMemoryRecordCardStore recordCardStore;
    private InMemoryRecordCardMusicRequests musicRequests;
    private FakeRecordCardObjectStore objectStore;
    private RetryRecordCardMusicHandler retryHandler;
    private UpdateRecordCardHandler updateHandler;
    private CompleteRecordCardDraftHandler completeHandler;

    @BeforeEach
    void setUp() {
        owner = UuidCreator.getTimeOrderedEpoch();
        recordCardStore = new InMemoryRecordCardStore();
        musicRequests = new InMemoryRecordCardMusicRequests();
        InMemoryRecordCardDraftStore draftStore = new InMemoryRecordCardDraftStore();
        objectStore = new FakeRecordCardObjectStore();
        RecordCardDraftProperties properties = new RecordCardDraftProperties(Duration.ofMinutes(30),
                DataSize.ofMegabytes(100), DataSize.ofMegabytes(1), DataSize.ofMegabytes(10), Duration.ofSeconds(30));
        retryHandler = new RetryRecordCardMusicHandler(recordCardStore, recordCardStore, recordCardStore,
                musicRequests);
        RecordCardEditor editor = new RecordCardEditor(recordCardStore, recordCardStore, recordCardStore,
                recordCardStore, musicRequests, recordCardStore);
        updateHandler = new UpdateRecordCardHandler(properties, editor,
                new RecordCardUploadIssuer(properties, new FakeImageIdGenerator(), new FakeUploadUrlGenerator(),
                        new FakeMultipartUploadStarter()),
                new FakeRecordCardIdGenerator(3L), draftStore, query -> null);
        completeHandler = new CompleteRecordCardDraftHandler(properties, draftStore, draftStore, draftStore,
                objectStore, objectStore,
                new RecordCardDraftSaver(recordCardStore, recordCardStore, recordCardStore, recordCardStore,
                        recordCardStore, musicRequests),
                new RecordCardEditDraftSaver(editor));

        // 기본곡·A는 끝났고 B만 실패한 카드
        recordCardStore.addRecord(RECORD_ID, owner);
        RecordCard card = RecordCard.create(UuidCreator.getTimeOrderedEpoch(), RECORD_ID, owner,
                RecordCardVisibility.PUBLIC, RecordCardActivityType.DAILY, "한강 한 바퀴", DisplayOrder.FIRST,
                List.of(new RecordCard.ImageDraft(A, "record-cards/u/c/a/a.jpg", "노을"),
                        new RecordCard.ImageDraft(B, "record-cards/u/c/b/b.jpg", null)), 0);
        card.completeBaseMusic(MusicStatus.COMPLETED, "audio/base.wav");
        card.completeImageMusic(A, MusicStatus.COMPLETED, "audio/a.wav");
        card.completeImageMusic(B, MusicStatus.FAILED, null);
        recordCardStore.create(card);
        cardId = card.getRecordCardId().value();
    }

    @Test
    @DisplayName("실패한 사진만 key 없이 생성 중으로 저장하고 그 사진만 실은 요청 한 건을 보낸다 — 완료된 기본곡·사진은 key까지 그대로")
    void retriesFailedOnly() {
        // when
        retryHandler.handle(new RetryRecordCardMusicCommand(owner, cardId));

        // then
        RecordCard card = card();
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(card.getBaseMusicKey()).map(key -> key.value()).contains("audio/base.wav");
        assertThat(card.getImages()).extracting(RecordCardImage::getMusicStatus)
                .containsExactly(MusicStatus.COMPLETED, MusicStatus.GENERATING);
        assertThat(card.getImages().get(0).getMusicKey()).map(key -> key.value()).contains("audio/a.wav");
        assertThat(card.getImages().get(1).getMusicKey()).isEmpty();
        assertThat(musicRequests.requests()).singleElement().satisfies(request -> {
            assertThat(request.recordCardId()).isEqualTo(cardId);
            assertThat(request.photos()).containsExactly(new Photo(B, "record-cards/u/c/b/b.jpg", null));
        });
        assertThat(recordCardStore.snapshots()).isEmpty();
        assertThat(recordCardStore.updateCount()).isZero();
    }

    @Test
    @DisplayName("다시 만드는 중에 또 부르면 실패가 없어 아무것도 하지 않는다 — 요청은 처음 한 건뿐이다")
    void secondCallIsNoop() {
        // when
        retryHandler.handle(new RetryRecordCardMusicCommand(owner, cardId));
        retryHandler.handle(new RetryRecordCardMusicCommand(owner, cardId));

        // then
        assertThat(musicRequests.requests()).hasSize(1);
        assertThat(recordCardStore.musicSaveCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("남의 카드는 404이고 카드·요청이 그대로다")
    void othersCard() {
        // when & then
        UUID stranger = UuidCreator.getTimeOrderedEpoch();
        assertThatThrownBy(() -> retryHandler.handle(new RetryRecordCardMusicCommand(stranger, cardId)))
                .isInstanceOf(RecordCardNotFoundException.class);
        assertThat(card().getImages()).extracting(RecordCardImage::getMusicStatus)
                .containsExactly(MusicStatus.COMPLETED, MusicStatus.FAILED);
        assertThat(musicRequests.requests()).isEmpty();
    }

    @Test
    @DisplayName("수정 초안을 만든 뒤 다시 만들기를 해도 이력이 없어 초안 확정은 409가 아니라 반영된다")
    void retryDoesNotConflictWithPendingEdit() {
        // given — 새 사진이 든 수정 초안
        UpdateRecordCardResult draft = updateHandler.handle(new UpdateRecordCardCommand(owner, cardId, null, null,
                null, List.of(new UpdateRecordCardCommand.Image(A, null, null, null, null, "노을"),
                        new UpdateRecordCardCommand.Image(B, null, null, null, null, null),
                        new UpdateRecordCardCommand.Image(null, "image/jpeg", 1_000L, 100L, "n.jpg", "결승선")), null));
        Upload upload = draft.draft().uploads().getFirst();

        // when — 그사이 다시 만들기, 그다음 올리고 확정
        retryHandler.handle(new RetryRecordCardMusicCommand(owner, cardId));
        objectStore.putObject(upload.imageKey(), 1_000L, "image/jpeg");
        objectStore.putObject(thumbnailKeyOf(upload.imageKey()), 100L, "image/jpeg");
        UUID confirmed = completeHandler.handle(
                new CompleteRecordCardDraftCommand(owner, draft.draft().recordCardDraftId())).recordCardId();

        // then
        assertThat(confirmed).isEqualTo(cardId);
        assertThat(card().getImages()).extracting(image -> image.getRecordCardImageId().value())
                .containsExactly(A, B, upload.recordCardImageId());
        assertThat(musicRequests.requests()).hasSize(2);
    }

    private RecordCard card() {
        return recordCardStore.cardOf(RECORD_ID).orElseThrow();
    }
}
