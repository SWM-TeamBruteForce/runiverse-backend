package com.runiverse.running_service.unit_test.recordcard.application;

import com.runiverse.running_service.application.common.port.out.RecordInboxEventPort;
import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicCommand.PhotoMusic;
import com.runiverse.running_service.application.recordcard.command.completemusic.CompleteRecordCardMusicHandler;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardMusicPort;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 음악 생성 완료 반영 핸들러 단위 테스트")
class CompleteRecordCardMusicHandlerTest {

    private static final String TOPIC = "test.record-card.music.generated";
    private static final String EVENT_ID = "1c298034-bf6f-4f06-a983-dc3b59822e74";
    private static final String PAYLOAD = "{\"inference_id\":\"1c298034-bf6f-4f06-a983-dc3b59822e74\"}";
    private static final UUID CARD_ID = UUID.fromString("01a1ee0f-4fee-7741-a7b1-7eeb86bb369f");
    private static final UUID USER_ID = UUID.fromString("01a1e874-91e8-73b4-9680-d7f377cbd009");
    private static final UUID IMAGE_A = UUID.fromString("01a1ee0f-4fee-7d58-a7b2-925ef4d37f36");
    private static final UUID IMAGE_B = UUID.fromString("01a1ee0f-5029-7488-9b2d-c7b5d4580997");
    private static final UUID IMAGE_REMOVED = UUID.fromString("01a1ee0f-502a-7351-978b-3c6c69622e2a");
    private static final String BASE_KEY = "audio/test/u/DAILY/c/base.wav";
    private static final String KEY_A = "audio/test/u/DAILY/c/a.wav";
    private static final String KEY_B = "audio/test/u/DAILY/c/b.wav";
    private static final String OLD_KEY = "audio/test/u/DAILY/c/old.wav";

    @Mock
    private RecordInboxEventPort recordInboxEventPort;
    @Mock
    private LoadRecordCardForUpdatePort loadRecordCardForUpdatePort;
    @Mock
    private SaveRecordCardMusicPort saveRecordCardMusicPort;

    @InjectMocks
    private CompleteRecordCardMusicHandler handler;

    @Test
    @DisplayName("처음 받은 성공 결과면 기본곡과 담긴 사진을 COMPLETED와 받은 key로 바꾸고 저장한다")
    void completesOnSuccess() {
        // given
        RecordCard card = card(MusicStatus.GENERATING, null,
                image(IMAGE_A, MusicStatus.GENERATING, null, 0), image(IMAGE_B, MusicStatus.GENERATING, null, 1));
        givenFirstEvent(card);

        // when
        handler.handle(command(MusicStatus.COMPLETED, BASE_KEY,
                List.of(new PhotoMusic(IMAGE_A, KEY_A), new PhotoMusic(IMAGE_B, KEY_B))));

        // then
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(card.getBaseMusicKey()).contains(new MusicKey(BASE_KEY));
        assertThat(imageOf(card, IMAGE_A).getMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(imageOf(card, IMAGE_A).getMusicKey()).contains(new MusicKey(KEY_A));
        assertThat(imageOf(card, IMAGE_B).getMusicKey()).contains(new MusicKey(KEY_B));
        verify(recordInboxEventPort).recordIfAbsent(TOPIC, EVENT_ID, CARD_ID.toString(), PAYLOAD);
        verify(saveRecordCardMusicPort).saveMusic(card);
    }

    @Test
    @DisplayName("실패 결과면 기본곡과 담긴 사진을 key 없이 FAILED로 바꾼다")
    void failsOnFailure() {
        // given
        RecordCard card = card(MusicStatus.GENERATING, null, image(IMAGE_A, MusicStatus.GENERATING, null, 0));
        givenFirstEvent(card);

        // when
        handler.handle(command(MusicStatus.FAILED, null, List.of(new PhotoMusic(IMAGE_A, null))));

        // then
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.FAILED);
        assertThat(card.getBaseMusicKey()).isEmpty();
        assertThat(imageOf(card, IMAGE_A).getMusicStatus()).isEqualTo(MusicStatus.FAILED);
        assertThat(imageOf(card, IMAGE_A).getMusicKey()).isEmpty();
        verify(saveRecordCardMusicPort).saveMusic(card);
    }

    @Test
    @DisplayName("이미 처리한 이벤트면 카드를 읽지도 저장하지도 않는다")
    void skipsDuplicateEvent() {
        // given
        given(recordInboxEventPort.recordIfAbsent(TOPIC, EVENT_ID, CARD_ID.toString(), PAYLOAD)).willReturn(false);

        // when
        handler.handle(command(MusicStatus.COMPLETED, BASE_KEY, List.of(new PhotoMusic(IMAGE_A, KEY_A))));

        // then
        verifyNoInteractions(loadRecordCardForUpdatePort, saveRecordCardMusicPort);
    }

    @Test
    @DisplayName("지운 카드면 저장하지 않고 끝낸다 — inbox에는 남아 다시 받아도 처리하지 않는다")
    void skipsMissingCard() {
        // given
        given(recordInboxEventPort.recordIfAbsent(TOPIC, EVENT_ID, CARD_ID.toString(), PAYLOAD)).willReturn(true);
        given(loadRecordCardForUpdatePort.loadForUpdate(new RecordCardId(CARD_ID))).willReturn(Optional.empty());

        // when
        handler.handle(command(MusicStatus.COMPLETED, BASE_KEY, List.of()));

        // then
        verify(recordInboxEventPort).recordIfAbsent(TOPIC, EVENT_ID, CARD_ID.toString(), PAYLOAD);
        verify(saveRecordCardMusicPort, never()).saveMusic(any());
    }

    @Test
    @DisplayName("이미 끝난 기본곡은 늦게 온 결과로 덮지 않는다 — 사진은 그대로 반영한다")
    void keepsFinishedBaseMusic() {
        // given
        RecordCard card = card(MusicStatus.COMPLETED, OLD_KEY, image(IMAGE_A, MusicStatus.GENERATING, null, 0));
        givenFirstEvent(card);

        // when
        handler.handle(command(MusicStatus.FAILED, null, List.of(new PhotoMusic(IMAGE_A, null))));

        // then
        assertThat(card.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(card.getBaseMusicKey()).contains(new MusicKey(OLD_KEY));
        assertThat(imageOf(card, IMAGE_A).getMusicStatus()).isEqualTo(MusicStatus.FAILED);
    }

    @Test
    @DisplayName("이미 끝난 사진 음악은 덮지 않는다 — 실패로 끝난 음악에 성공이 와도 그대로다")
    void keepsFinishedPhotoMusic() {
        // given
        RecordCard card = card(MusicStatus.GENERATING, null,
                image(IMAGE_A, MusicStatus.FAILED, null, 0), image(IMAGE_B, MusicStatus.GENERATING, null, 1));
        givenFirstEvent(card);

        // when
        handler.handle(command(MusicStatus.COMPLETED, BASE_KEY,
                List.of(new PhotoMusic(IMAGE_A, KEY_A), new PhotoMusic(IMAGE_B, KEY_B))));

        // then
        assertThat(imageOf(card, IMAGE_A).getMusicStatus()).isEqualTo(MusicStatus.FAILED);
        assertThat(imageOf(card, IMAGE_A).getMusicKey()).isEmpty();
        assertThat(imageOf(card, IMAGE_B).getMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
    }

    @Test
    @DisplayName("카드에 없는 사진의 결과는 건너뛰고 사진 행을 만들지 않는다")
    void skipsRemovedPhoto() {
        // given
        RecordCard card = card(MusicStatus.GENERATING, null, image(IMAGE_A, MusicStatus.GENERATING, null, 0));
        givenFirstEvent(card);

        // when
        handler.handle(command(MusicStatus.COMPLETED, BASE_KEY,
                List.of(new PhotoMusic(IMAGE_A, KEY_A), new PhotoMusic(IMAGE_REMOVED, KEY_B))));

        // then
        assertThat(card.getImages()).extracting(image -> image.getRecordCardImageId().value())
                .containsExactly(IMAGE_A);
        assertThat(imageOf(card, IMAGE_A).getMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        verify(saveRecordCardMusicPort).saveMusic(card);
    }

    @Test
    @DisplayName("결과에 담기지 않은 사진은 GENERATING이어도 건드리지 않는다 — 다른 요청의 사진일 수 있다")
    void leavesPhotoNotInResult() {
        // given
        RecordCard card = card(MusicStatus.GENERATING, null,
                image(IMAGE_A, MusicStatus.GENERATING, null, 0), image(IMAGE_B, MusicStatus.GENERATING, null, 1));
        givenFirstEvent(card);

        // when
        handler.handle(command(MusicStatus.COMPLETED, BASE_KEY, List.of(new PhotoMusic(IMAGE_A, KEY_A))));

        // then
        assertThat(imageOf(card, IMAGE_A).getMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(imageOf(card, IMAGE_B).getMusicStatus()).isEqualTo(MusicStatus.GENERATING);
        assertThat(imageOf(card, IMAGE_B).getMusicKey()).isEmpty();
    }

    private void givenFirstEvent(RecordCard card) {
        given(recordInboxEventPort.recordIfAbsent(TOPIC, EVENT_ID, CARD_ID.toString(), PAYLOAD)).willReturn(true);
        given(loadRecordCardForUpdatePort.loadForUpdate(new RecordCardId(CARD_ID))).willReturn(Optional.of(card));
    }

    private static CompleteRecordCardMusicCommand command(MusicStatus result, String baseMusicKey,
                                                          List<PhotoMusic> photoMusics) {
        boolean failed = result == MusicStatus.FAILED;
        return new CompleteRecordCardMusicCommand(TOPIC, EVENT_ID, CARD_ID, result, baseMusicKey, photoMusics,
                failed ? "INFERENCE" : null, failed ? "MUSIC_GENERATION_OOM" : null, PAYLOAD);
    }

    private static RecordCard card(MusicStatus baseStatus, String baseKey, RecordCardImage... images) {
        return RecordCard.restore(CARD_ID)
                .runningRecordId(501L)
                .userId(USER_ID)
                .visibility(RecordCardVisibility.PRIVATE)
                .activityType(RecordCardActivityType.DAILY)
                .baseMusicStatus(baseStatus)
                .baseMusicKey(baseKey)
                .memo("한강 한 바퀴")
                .displayOrder(1)
                .thumbnailImageKey(images.length == 0 ? null : images[0].getImageKey().value())
                .images(List.of(images))
                .build();
    }

    private static RecordCardImage image(UUID imageId, MusicStatus status, String musicKey, int sortOrder) {
        return RecordCardImage.restore(imageId, "record-cards/u/c/" + imageId + "/IMG.jpg", null, musicKey, status,
                sortOrder);
    }

    private static RecordCardImage imageOf(RecordCard card, UUID imageId) {
        return card.getImages().stream()
                .filter(image -> image.getRecordCardImageId().value().equals(imageId))
                .findFirst()
                .orElseThrow();
    }
}
