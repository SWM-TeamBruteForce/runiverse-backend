package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.RelationLevelReader;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardViewUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDetailPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDetail;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailHandler;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.RelationLevel;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 상세 조회 단위 테스트")
class GetRecordCardDetailHandlerTest {

    private static final UUID CARD_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID AUTHOR_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID VIEWER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID IMAGE_A = UuidCreator.getTimeOrderedEpoch();
    private static final UUID IMAGE_B = UuidCreator.getTimeOrderedEpoch();
    private static final String KEY_A = "record-cards/u/c/a/a.jpg";
    private static final String KEY_B = "record-cards/u/c/b/b.jpg";
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 7, 25, 19, 0, 30);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 25, 21, 10);

    @Mock
    private LoadRecordCardDetailPort loadRecordCardDetailPort;
    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;
    @Mock
    private RelationLevelReader relationLevelReader;
    @Mock
    private GenerateRecordCardViewUrlPort generateRecordCardViewUrlPort;
    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private GetRecordCardDetailHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetRecordCardDetailHandler(loadRecordCardDetailPort, loadPlayerProfilesPort,
                relationLevelReader, generateRecordCardViewUrlPort, generateViewUrlPort);
    }

    @Test
    @DisplayName("본인 카드 — 작성자·지표·선 아트·사진·음악을 싣고 내 카드로 표시한다")
    void ownCard() {
        // given
        givenCard(card(RecordCardVisibility.PRIVATE, MusicStatus.COMPLETED, "Night_Walk.wav",
                List.of(image(IMAGE_A, KEY_A, "노을", MusicStatus.COMPLETED, "a.wav"),
                        image(IMAGE_B, KEY_B, null, MusicStatus.GENERATING, null)),
                List.of(PolylineEncoder.encodePoints(List.of(
                        new RoutePoint(37.50, 127.00), new RoutePoint(37.51, 127.00))))));
        givenAuthor("profiles/me.jpg");
        givenLevel(AUTHOR_ID, RelationLevel.SELF);
        when(generateViewUrlPort.generate("profiles/me.jpg")).thenReturn("https://profile");
        when(generateRecordCardViewUrlPort.imageUrl(KEY_A)).thenReturn("https://a");
        when(generateRecordCardViewUrlPort.imageUrl(KEY_B)).thenReturn("https://b");
        when(generateRecordCardViewUrlPort.musicUrl("Night_Walk.wav")).thenReturn("https://base");
        when(generateRecordCardViewUrlPort.musicUrl("a.wav")).thenReturn("https://music-a");

        // when
        GetRecordCardDetailResult result = handler.handle(new GetRecordCardDetailQuery(AUTHOR_ID, CARD_ID));

        // then
        assertThat(result.recordCardId()).isEqualTo(CARD_ID);
        assertThat(result.mine()).isTrue();
        assertThat(result.author()).isEqualTo(
                new GetRecordCardDetailResult.Author(AUTHOR_ID, "동완러너", "https://profile"));
        assertThat(result.visibility()).isEqualTo(RecordCardVisibility.PRIVATE);
        assertThat(result.activityType()).isEqualTo(RecordCardActivityType.DAILY);
        assertThat(result.memo()).isEqualTo("한강 야간 러닝");
        assertThat(result.record().runningRecordId()).isEqualTo(501L);
        assertThat(result.record().startedAt()).isEqualTo(STARTED_AT);
        assertThat(result.record().totalDistanceMeters()).isEqualTo(5020);
        assertThat(result.record().totalDurationSeconds()).isEqualTo(1800);
        assertThat(result.record().averagePaceSecondsPerKm()).isEqualTo(359);
        assertThat(result.record().routeArt()).hasSize(1);
        assertThat(result.baseMusic()).isEqualTo(new GetRecordCardDetailResult.Music(MusicStatus.COMPLETED, "https://base"));
        assertThat(result.images()).containsExactly(
                new GetRecordCardDetailResult.Image(IMAGE_A, "https://a", "노을", true,
                        new GetRecordCardDetailResult.Music(MusicStatus.COMPLETED, "https://music-a")),
                new GetRecordCardDetailResult.Image(IMAGE_B, "https://b", null, false,
                        new GetRecordCardDetailResult.Music(MusicStatus.GENERATING, null)));
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("친구가 친구 공개 카드를 보면 보이고, 내 카드가 아니다")
    void friendSeesFriendsCard() {
        // given
        givenCard(card(RecordCardVisibility.FRIENDS, MusicStatus.GENERATING, null, List.of(), List.of()));
        givenAuthor(null);
        givenLevel(VIEWER_ID, RelationLevel.FRIEND);

        // when
        GetRecordCardDetailResult result = handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID));

        // then
        assertThat(result.mine()).isFalse();
        assertThat(result.author().profileImageUrl()).isNull();
        assertThat(result.baseMusic()).isEqualTo(new GetRecordCardDetailResult.Music(MusicStatus.GENERATING, null));
        assertThat(result.images()).isEmpty();
        assertThat(result.record().routeArt()).isEmpty();
        verify(generateRecordCardViewUrlPort, never()).musicUrl(any());
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("실패한 음악은 URL이 없다")
    void failedMusicHasNoUrl() {
        // given
        givenCard(card(RecordCardVisibility.PUBLIC, MusicStatus.FAILED, null, List.of(), List.of()));
        givenAuthor(null);
        givenLevel(VIEWER_ID, RelationLevel.OTHER);

        // when
        GetRecordCardDetailResult result = handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID));

        // then
        assertThat(result.baseMusic()).isEqualTo(new GetRecordCardDetailResult.Music(MusicStatus.FAILED, null));
    }

    @Test
    @DisplayName("카드가 없으면 404이고 더 읽지 않는다")
    void cardNotFound() {
        // given
        when(loadRecordCardDetailPort.loadDetail(new RecordCardId(CARD_ID))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(loadPlayerProfilesPort, relationLevelReader, generateRecordCardViewUrlPort,
                generateViewUrlPort);
    }

    @Test
    @DisplayName("작성자가 탈퇴했으면 공개 카드여도 404이고 관계를 읽지 않는다")
    void authorDeleted() {
        // given
        givenCard(card(RecordCardVisibility.PUBLIC, MusicStatus.GENERATING, null, List.of(), List.of()));
        when(loadPlayerProfilesPort.loadProfiles(List.of(AUTHOR_ID))).thenReturn(Map.of());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(relationLevelReader, generateRecordCardViewUrlPort, generateViewUrlPort);
    }

    @Test
    @DisplayName("공개 범위 밖이면 404이고 사진·음악 URL을 만들지 않는다")
    void notVisible() {
        // given — 남이 비공개 카드를 본다
        givenCard(card(RecordCardVisibility.PRIVATE, MusicStatus.COMPLETED, "Night_Walk.wav",
                List.of(image(IMAGE_A, KEY_A, null, MusicStatus.COMPLETED, "a.wav")), List.of()));
        givenAuthor("profiles/me.jpg");
        givenLevel(VIEWER_ID, RelationLevel.OTHER);

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(generateRecordCardViewUrlPort, generateViewUrlPort);
    }

    @Test
    @DisplayName("차단 관계면 공개 카드여도 404다")
    void blocked() {
        // given
        givenCard(card(RecordCardVisibility.PUBLIC, MusicStatus.GENERATING, null, List.of(), List.of()));
        givenAuthor(null);
        givenLevel(VIEWER_ID, RelationLevel.BLOCKED);

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(generateRecordCardViewUrlPort, generateViewUrlPort);
    }

    @Test
    @DisplayName("같이 뛴 사람을 작성자와 같은 모양으로 싣는다 — 탈퇴자(프로필 없음)와 보는 사람과 차단 관계인 사람은 뺀다")
    void companions() {
        // given — 셋이 같이 뛰었다: 친구 하나, 탈퇴자 하나, 보는 사람과 차단 관계 하나
        UUID friend = UuidCreator.getTimeOrderedEpoch();
        UUID withdrawn = UuidCreator.getTimeOrderedEpoch();
        UUID blocked = UuidCreator.getTimeOrderedEpoch();
        RecordCardDetail base = card(RecordCardVisibility.PUBLIC, MusicStatus.GENERATING, null, List.of(), List.of());
        givenCard(new RecordCardDetail(base.recordCardId(), base.userId(), base.visibility(), base.activityType(),
                base.memo(), null, base.baseMusicStatus(), null, List.of(), base.record(),
                List.of(friend, withdrawn, blocked), CREATED_AT, CREATED_AT));
        when(loadPlayerProfilesPort.loadProfiles(List.of(friend, withdrawn, blocked, AUTHOR_ID))).thenReturn(Map.of(
                AUTHOR_ID, new PlayerProfile(AUTHOR_ID, "동완러너", null, null),
                friend, new PlayerProfile(friend, "달리는곰", "profiles/bear.jpg", null),
                blocked, new PlayerProfile(blocked, "막힌사람", null, null)));
        givenLevel(VIEWER_ID, RelationLevel.OTHER);
        when(relationLevelReader.read(new UserId(VIEWER_ID), new UserId(friend))).thenReturn(RelationLevel.FRIEND);
        when(relationLevelReader.read(new UserId(VIEWER_ID), new UserId(blocked))).thenReturn(RelationLevel.BLOCKED);
        when(generateViewUrlPort.generate("profiles/bear.jpg")).thenReturn("https://bear");

        // when
        GetRecordCardDetailResult result = handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID));

        // then
        assertThat(result.companions())
                .containsExactly(new GetRecordCardDetailResult.Author(friend, "달리는곰", "https://bear"));
    }

    @Test
    @DisplayName("솔로 러닝처럼 같이 뛴 사람이 없으면 빈 목록이고 관계를 더 읽지 않는다")
    void noCompanions() {
        // given
        givenCard(card(RecordCardVisibility.PUBLIC, MusicStatus.GENERATING, null, List.of(), List.of()));
        givenAuthor(null);
        givenLevel(VIEWER_ID, RelationLevel.OTHER);

        // when
        GetRecordCardDetailResult result = handler.handle(new GetRecordCardDetailQuery(VIEWER_ID, CARD_ID));

        // then
        assertThat(result.companions()).isEmpty();
        verify(relationLevelReader).read(new UserId(VIEWER_ID), new UserId(AUTHOR_ID));
        verifyNoMoreInteractions(relationLevelReader);
    }

    private void givenCard(RecordCardDetail card) {
        when(loadRecordCardDetailPort.loadDetail(new RecordCardId(CARD_ID))).thenReturn(Optional.of(card));
    }

    private void givenAuthor(String profileImageKey) {
        when(loadPlayerProfilesPort.loadProfiles(List.of(AUTHOR_ID)))
                .thenReturn(Map.of(AUTHOR_ID, new PlayerProfile(AUTHOR_ID, "동완러너", profileImageKey, null)));
    }

    private void givenLevel(UUID viewerId, RelationLevel level) {
        when(relationLevelReader.read(new UserId(viewerId), new UserId(AUTHOR_ID))).thenReturn(level);
    }

    // 썸네일은 첫 사진(KEY_A)이다
    private static RecordCardDetail card(RecordCardVisibility visibility, MusicStatus baseStatus, String baseKey,
                                         List<RecordCardDetail.Image> images, List<String> polylines) {
        return new RecordCardDetail(CARD_ID, AUTHOR_ID, visibility, RecordCardActivityType.DAILY, "한강 야간 러닝",
                images.isEmpty() ? null : KEY_A, baseStatus, baseKey, images,
                new RecordCardDetail.Record(501L, STARTED_AT, 5020, 1800, 359, polylines), List.of(),
                CREATED_AT, CREATED_AT);
    }

    private static RecordCardDetail.Image image(UUID id, String key, String caption, MusicStatus status,
                                                String musicKey) {
        return new RecordCardDetail.Image(id, key, caption, status, musicKey);
    }
}
