package com.runiverse.running_service.integration_test.user;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityHandler;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityQuery;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("닉네임 사용 가능 여부 확인 통합 테스트")
public class CheckNicknameAvailabilityIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String OWNER_EMAIL = "runner@runiverse.com";
    private static final String OWNER_NICKNAME = "러너킴";
    private static final String FREE_NICKNAME = "완두콩";
    private static final String NEW_NICKNAME = "동완러너";

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private ChangeNicknameHandler changeNicknameHandler;
    private CheckNicknameAvailabilityHandler checkNicknameAvailabilityHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        changeNicknameHandler = new ChangeNicknameHandler(
                onboardingStore,  // LoadNicknamePort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // UpdateNicknamePort
        );
        checkNicknameAvailabilityHandler = new CheckNicknameAvailabilityHandler(
                onboardingStore   // CheckNicknameDuplicatePort
        );
    }

    private boolean available(String nickname) {
        return checkNicknameAvailabilityHandler
                .handle(new CheckNicknameAvailabilityQuery(nickname))
                .available();
    }

    // 가입부터 온보딩까지 마친 유저를 만든다. 닉네임은 온보딩에서 처음 점유된다
    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1999, 5, 20),
                330, new BigDecimal("70.5"), new BigDecimal("175.0")));
        return userId;
    }

    @Test
    @DisplayName("아무도 온보딩하지 않았으면 어떤 닉네임이든 사용 가능하다")
    void everyNicknameIsAvailableWhenNobodyOnboarded() {
        // when & then
        assertThat(available(OWNER_NICKNAME)).isTrue();
        assertThat(available(FREE_NICKNAME)).isTrue();
    }

    @Test
    @DisplayName("온보딩으로 점유된 닉네임은 사용 불가, 나머지는 사용 가능하다")
    void onboardedNicknameIsNotAvailable() {
        // given
        onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);

        // when & then
        assertThat(available(OWNER_NICKNAME)).isFalse();
        assertThat(available(FREE_NICKNAME)).isTrue();
    }

    @Test
    @DisplayName("확인 결과가 온보딩 API의 중복 판정과 어긋나지 않는다")
    void answerAgreesWithOnboarding() {
        // given -> 사전 확인이 사용 가능이라고 한 닉네임은 실제 온보딩도 통과해야 한다
        assertThat(available(OWNER_NICKNAME)).isTrue();

        // when
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);

        // then
        assertThat(onboardingStore.nicknameOf(userId)).contains(OWNER_NICKNAME);
        assertThat(available(OWNER_NICKNAME)).isFalse();
    }

    @Test
    @DisplayName("닉네임을 변경하면 놓아준 닉네임은 다시 사용 가능해지고 새 닉네임이 잠긴다")
    void changedNicknameFreesThePreviousOne() {
        // given
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);

        // when
        changeNicknameHandler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME));

        // then
        assertThat(available(OWNER_NICKNAME)).isTrue();
        assertThat(available(NEW_NICKNAME)).isFalse();
    }

    @Test
    @DisplayName("호출자를 구분하지 않으므로 자기 닉네임도 사용 불가로 답한다")
    void requesterOwnNicknameIsAlsoReportedTaken() {
        // given -> 온보딩 입력 전 전용이라 userId를 받지 않는다. 현재 계약을 고정해둔다.
        //          프로필 수정 화면에서 이 API를 재사용하려면 여기가 먼저 깨진다 -
        //          닉네임 변경은 자기 닉네임을 no-op으로 통과시키므로 두 API가 어긋난다
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);

        // when & then
        assertThat(available(OWNER_NICKNAME)).isFalse();
        assertThat(changeNicknameHandler.handle(
                new ChangeNicknameCommand(userId, OWNER_NICKNAME)).nickname())
                .isEqualTo(OWNER_NICKNAME);
    }
}
