package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileHandler;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileHandler;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileResult;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.FakeViewUrlGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("프로필 요약 조회 통합 테스트")
public class GetUserProfileIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";
    private static final String NICKNAME = "러너킴";
    private static final String OTHER_NICKNAME = "완두콩";
    private static final String INTRODUCTION = "즐겁게 달려요";
    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");
    private static final int AVG_PACE = 330;

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private ChangeMyProfileHandler changeMyProfileHandler;
    private GetUserProfileHandler getUserProfileHandler;
    private FakeViewUrlGenerator viewUrlGenerator;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        viewUrlGenerator = new FakeViewUrlGenerator();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        changeMyProfileHandler = new ChangeMyProfileHandler(
                userStore,        // LoadUserByIdPort
                userStore,        // UpdateIntroductionPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore   // UpdateOnboardingPort
        );
        getUserProfileHandler = new GetUserProfileHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // LoadNicknamePort
                viewUrlGenerator  // GenerateViewUrlPort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUp(email);
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", BIRTHDAY, AVG_PACE, WEIGHT, HEIGHT));
        return userId;
    }

    private GetUserProfileResult profileOf(UUID viewerId, UUID targetUserId) {
        return getUserProfileHandler.handle(new GetUserProfileQuery(viewerId, targetUserId));
    }

    @Test
    @DisplayName("가입만 마친 사용자는 닉네임도 소개글도 없이 조회된다")
    void returnsEmptyProfileRightAfterSignUp() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        GetUserProfileResult result = profileOf(userId, userId);

        // then -> 온보딩 전에는 닉네임이 아직 없다
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.nickname()).isNull();
        assertThat(result.introduction()).isNull();
        assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("본인 프로필을 열면 isMe가 true이고 친구 상태가 없다")
    void marksOwnProfile() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        GetUserProfileResult result = profileOf(userId, userId);

        // then -> 본인에게는 친구 관계가 없어 NONE이 아니라 null이다
        assertThat(result.isMe()).isTrue();
        assertThat(result.friendStatus()).isNull();
        assertThat(result.nickname()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("다른 사용자의 프로필을 열면 isMe가 false이고 친구 상태가 담긴다")
    void marksOtherUserProfile() {
        // given
        UUID viewerId = onboardedUser(EMAIL, NICKNAME);
        UUID otherUserId = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);

        // when
        GetUserProfileResult result = profileOf(viewerId, otherUserId);

        // then -> 친구 요청 버튼을 가르는 값이 필요하다
        assertThat(result.isMe()).isFalse();
        assertThat(result.friendStatus()).isNotNull();
        assertThat(result.nickname()).isEqualTo(OTHER_NICKNAME);
    }

    @Test
    @DisplayName("소개글을 쓰면 조회에 반영된다")
    void reflectsChangedIntroduction() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                userId, INTRODUCTION, null, null, null, null));

        // then
        assertThat(profileOf(userId, userId).introduction()).isEqualTo(INTRODUCTION);
    }

    @Test
    @DisplayName("소개글을 지우면 빈 문자열이 아니라 null로 답한다")
    void returnsNullAfterIntroductionCleared() {
        // given -> 빈 문자열은 "지워 달라"는 요청이다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                userId, INTRODUCTION, null, null, null, null));

        // when
        changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                userId, "", null, null, null, null));

        // then -> 조회 응답의 "값 없음"은 null로 통일한다
        assertThat(profileOf(userId, userId).introduction()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 값이 섞이지 않는다")
    void doesNotLeakOtherUsersProfile() {
        // given
        UUID viewerId = onboardedUser(EMAIL, NICKNAME);
        UUID otherUserId = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);
        changeMyProfileHandler.handle(new ChangeMyProfileCommand(
                otherUserId, INTRODUCTION, null, null, null, null));

        // when & then
        assertThat(profileOf(viewerId, viewerId).introduction()).isNull();
        assertThat(profileOf(viewerId, otherUserId).introduction()).isEqualTo(INTRODUCTION);
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 조회할 수 없다")
    void throwsForUnknownUser() {
        // given
        UUID viewerId = signUp(EMAIL);

        // when & then -> 탈퇴한 사용자도 같은 경로로 걸러진다
        assertThatThrownBy(() -> profileOf(viewerId, UuidCreator.getTimeOrderedEpoch()))
                .isInstanceOf(ProfileNotFoundException.class);
        assertThat(viewUrlGenerator.isEmpty()).isTrue();
    }
}
