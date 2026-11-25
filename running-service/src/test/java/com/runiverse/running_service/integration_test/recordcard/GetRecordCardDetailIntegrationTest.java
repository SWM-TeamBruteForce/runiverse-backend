package com.runiverse.running_service.integration_test.recordcard;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.common.RelationLevelReader;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailHandler;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.domain.relationship.UserRelationship;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeRecordCardViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import com.runiverse.running_service.integration_test.fake.InMemoryPlayerProfileStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRecordCardStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserRelationshipStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 8-1 상세 — 가입·온보딩한 사용자, 관계 저장소, 공개 범위 규칙을 실제로 엮어 조회한다. 스프링 없이 페이크로 조립한다.
// JPQL·presigned 서명은 어댑터 단위 테스트와 E2E가 본다
@DisplayName("기록 카드 상세(8-1) 통합 테스트")
class GetRecordCardDetailIntegrationTest extends IntegrationTestSupport {

    private static final long RECORD_ID = 501L;
    private static final String PASSWORD = "Password123!";
    private static final String KEY_A = "record-cards/u/c/a/IMG_1.jpg";
    private static final String KEY_B = "record-cards/u/c/b/IMG_2.jpg";

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private InMemoryRecordCardStore recordCardStore;
    private InMemoryUserRelationshipStore relationshipStore;
    private InMemoryPlayerProfileStore playerProfileStore;
    private FakeRecordCardViewUrlGenerator recordCardViewUrls;
    private FakeViewUrlGenerator profileViewUrls;
    private GetRecordCardDetailHandler handler;

    private UUID author;
    private UUID viewer;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(userStore, onboardingStore, onboardingStore,
                onboardingStore);
        recordCardStore = new InMemoryRecordCardStore();
        relationshipStore = new InMemoryUserRelationshipStore();
        playerProfileStore = new InMemoryPlayerProfileStore(userStore, onboardingStore);
        recordCardViewUrls = new FakeRecordCardViewUrlGenerator();
        profileViewUrls = new FakeViewUrlGenerator();
        handler = new GetRecordCardDetailHandler(recordCardStore, playerProfileStore,
                new RelationLevelReader(relationshipStore), recordCardViewUrls, profileViewUrls);

        author = onboardedUser("author@runiverse.com", "작성자");
        viewer = onboardedUser("viewer@runiverse.com", "보는사람");
    }

    @Test
    @DisplayName("작성자는 비공개 카드를 본다 — 지표·선 아트·사진 순서·썸네일, 다 만든 음악만 URL이 있다")
    void authorSeesOwnPrivateCard() {
        // given — 기본곡·첫 사진은 완료, 둘째 사진은 생성 중
        RecordCard card = card(RecordCardVisibility.PRIVATE, true);
        card.completeBaseMusic(MusicStatus.COMPLETED, "Night_Walk.wav");
        card.completeImageMusic(card.getImages().getFirst().getRecordCardImageId().value(), MusicStatus.COMPLETED,
                "a.wav");
        save(card);
        recordCardStore.addSegments(RECORD_ID, List.of(
                PolylineEncoder.encodePoints(List.of(new RoutePoint(37.50, 127.00), new RoutePoint(37.51, 127.00))),
                PolylineEncoder.encodePoints(List.of(new RoutePoint(37.51, 127.01), new RoutePoint(37.50, 127.01)))));

        // when
        GetRecordCardDetailResult result = handler.handle(
                new GetRecordCardDetailQuery(author, card.getRecordCardId().value()));

        // then
        assertThat(result.mine()).isTrue();
        assertThat(result.author().nickname()).isEqualTo("작성자");
        assertThat(result.author().profileImageUrl()).isNull();
        assertThat(result.record().totalDistanceMeters()).isEqualTo(InMemoryRecordCardStore.SUMMARY.totalDistance());
        assertThat(result.record().routeArt()).hasSize(2)
                .allSatisfy(segment -> assertThat(segment).allSatisfy(point -> {
                    assertThat(point.x()).isBetween(0.0, 1.0);
                    assertThat(point.y()).isBetween(0.0, 1.0);
                }));
        assertThat(result.baseMusic()).isEqualTo(new GetRecordCardDetailResult.Music(MusicStatus.COMPLETED,
                recordCardViewUrls.musicUrlOf("Night_Walk.wav")));
        assertThat(result.images()).extracting(GetRecordCardDetailResult.Image::imageUrl,
                        GetRecordCardDetailResult.Image::thumbnail, image -> image.music().musicUrl())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(recordCardViewUrls.imageUrlOf(KEY_A), true,
                                recordCardViewUrls.musicUrlOf("a.wav")),
                        org.assertj.core.groups.Tuple.tuple(recordCardViewUrls.imageUrlOf(KEY_B), false, null));
        assertThat(result.images().get(1).music().status()).isEqualTo(MusicStatus.GENERATING);
    }

    @Test
    @DisplayName("남은 비공개 카드를 볼 수 없고, URL도 만들지 않는다")
    void strangerCannotSeePrivate() {
        // given
        RecordCard card = save(card(RecordCardVisibility.PRIVATE, true));

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(viewer, card.getRecordCardId().value())))
                .isInstanceOf(RecordCardNotFoundException.class);
        assertThat(recordCardViewUrls.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("친구 공개 카드는 친구만 본다 — 요청만 보낸 사이는 못 본다")
    void friendsOnly() {
        // given
        RecordCard card = save(card(RecordCardVisibility.FRIENDS, false));
        UUID cardId = card.getRecordCardId().value();

        // when & then — 요청 중
        relationshipStore.seedRequest(viewer, author);
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(viewer, cardId)))
                .isInstanceOf(RecordCardNotFoundException.class);

        // when & then — 친구
        UUID friend = onboardedUser("friend@runiverse.com", "친구");
        relationshipStore.seedFriend(friend, author);
        GetRecordCardDetailResult result = handler.handle(new GetRecordCardDetailQuery(friend, cardId));
        assertThat(result.mine()).isFalse();
        assertThat(result.images()).isEmpty();
        assertThat(result.record().routeArt()).isEmpty();
    }

    @Test
    @DisplayName("전체 공개 카드도 작성자가 나를 막았으면 볼 수 없다")
    void blockedByAuthor() {
        // given
        RecordCard card = save(card(RecordCardVisibility.PUBLIC, false));
        UUID cardId = card.getRecordCardId().value();
        assertThat(handler.handle(new GetRecordCardDetailQuery(viewer, cardId)).mine()).isFalse();

        // when — 작성자 쪽 행만 BLOCKED다
        UserRelationship block = UserRelationship.none(author, viewer);
        block.block();
        relationshipStore.save(block);

        // then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(viewer, cardId)))
                .isInstanceOf(RecordCardNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 탈퇴하면 카드가 남아 있어도 공개 카드조차 볼 수 없다")
    void authorWithdrawn() {
        // given
        RecordCard card = save(card(RecordCardVisibility.PUBLIC, false));
        playerProfileStore.withdraw(author);

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(viewer, card.getRecordCardId().value())))
                .isInstanceOf(RecordCardNotFoundException.class);
    }

    @Test
    @DisplayName("없는 카드는 404다")
    void unknownCard() {
        assertThatThrownBy(() -> handler.handle(new GetRecordCardDetailQuery(viewer, UuidCreator.getTimeOrderedEpoch())))
                .isInstanceOf(RecordCardNotFoundException.class);
    }

    // 사진 두 장이면 첫 사진이 썸네일이다
    private RecordCard card(RecordCardVisibility visibility, boolean withImages) {
        recordCardStore.addRecord(RECORD_ID, author);
        List<RecordCard.ImageDraft> images = withImages
                ? List.of(new RecordCard.ImageDraft(UuidCreator.getTimeOrderedEpoch(), KEY_A, "노을"),
                        new RecordCard.ImageDraft(UuidCreator.getTimeOrderedEpoch(), KEY_B, null))
                : List.of();
        return RecordCard.create(UuidCreator.getTimeOrderedEpoch(), RECORD_ID, author, visibility,
                RecordCardActivityType.DAILY, "한강 한 바퀴", DisplayOrder.FIRST, images, withImages ? 0 : null);
    }

    private RecordCard save(RecordCard card) {
        recordCardStore.create(card);
        return card;
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1998, 5, 20), 330, new BigDecimal("70.0"),
                new BigDecimal("175.0")));
        return userId;
    }
}
