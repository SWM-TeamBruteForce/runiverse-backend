package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameHandler;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameResult;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("닉네임 변경 통합 테스트")
public class ChangeNicknameIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String OWNER_EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String OWNER_NICKNAME = "러너킴";
    private static final String OTHER_NICKNAME = "완두콩";
    private static final String NEW_NICKNAME = "동완러너";

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private ChangeNicknameHandler changeNicknameHandler;

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
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    // 가입부터 온보딩까지 마친 유저를 만든다. 닉네임은 온보딩에서 처음 생긴다
    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUp(email);
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1999, 5, 20),
                330, new BigDecimal("70.5"), new BigDecimal("175.0")));
        return userId;
    }

    @Test
    @DisplayName("닉네임을 바꾸면 저장소의 현재 닉네임이 갱신된다")
    void changeNicknameSuccess() {
        // given
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);

        // when
        ChangeNicknameResult result = changeNicknameHandler.handle(
                new ChangeNicknameCommand(userId, NEW_NICKNAME));

        // then
        assertThat(result.nickname()).isEqualTo(NEW_NICKNAME);
        assertThat(onboardingStore.nicknameOf(userId)).contains(NEW_NICKNAME);
    }

    @Test
    @DisplayName("온보딩을 마치지 않은 유저는 닉네임을 바꿀 수 없다")
    void notOnboardedUserIsRejected() {
        // given -> 가입만 하고 온보딩을 건너뛴 유저다
        UUID userId = signUp(OWNER_EMAIL);

        // when & then
        assertThatThrownBy(() -> changeNicknameHandler.handle(
                new ChangeNicknameCommand(userId, NEW_NICKNAME)))
                .isInstanceOf(OnboardingNotCompletedException.class);
        assertThat(onboardingStore.nicknameOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 유저도 온보딩 미완료로 막는다")
    void unknownUserIsRejected() {
        // when & then -> 계정 존재 여부를 따로 흘리지 않는다
        assertThatThrownBy(() -> changeNicknameHandler.handle(
                new ChangeNicknameCommand(UuidCreator.getTimeOrderedEpoch(), NEW_NICKNAME)))
                .isInstanceOf(OnboardingNotCompletedException.class);
    }

    @Test
    @DisplayName("자기가 쓰던 닉네임을 그대로 보내도 중복으로 막히지 않는다")
    void sameNicknameIsAccepted() {
        // given -> 중복 검사에 자기 닉네임이 걸리면 스스로에게 막힌다
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);

        // when
        ChangeNicknameResult result = changeNicknameHandler.handle(
                new ChangeNicknameCommand(userId, OWNER_NICKNAME));

        // then
        assertThat(result.nickname()).isEqualTo(OWNER_NICKNAME);
        assertThat(onboardingStore.nicknameOf(userId)).contains(OWNER_NICKNAME);
    }

    @Test
    @DisplayName("다른 유저가 쓰고 있는 닉네임으로는 바꿀 수 없고 기존 닉네임이 남는다")
    void nicknameTakenByOtherUserIsRejected() {
        // given
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);
        onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);

        // when & then
        assertThatThrownBy(() -> changeNicknameHandler.handle(
                new ChangeNicknameCommand(userId, OTHER_NICKNAME)))
                .isInstanceOf(NicknameAlreadyExistsException.class);
        assertThat(onboardingStore.nicknameOf(userId)).contains(OWNER_NICKNAME);
    }

    @Test
    @DisplayName("닉네임을 바꾸면 쓰던 닉네임이 풀려 다른 유저가 가져갈 수 있다")
    void previousNicknameIsReleased() {
        // given
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);
        UUID otherUserId = onboardedUser(OTHER_EMAIL, OTHER_NICKNAME);
        changeNicknameHandler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME));

        // when & then -> 이전 닉네임을 계속 점유하고 있으면 여기서 막힌다
        assertThatCode(() -> changeNicknameHandler.handle(
                new ChangeNicknameCommand(otherUserId, OWNER_NICKNAME)))
                .doesNotThrowAnyException();
        assertThat(onboardingStore.nicknameOf(otherUserId)).contains(OWNER_NICKNAME);
        assertThat(onboardingStore.nicknameOf(userId)).contains(NEW_NICKNAME);
    }

    @Test
    @DisplayName("온보딩 스냅샷은 그대로 두고 닉네임만 갱신한다")
    void otherOnboardingValuesAreUntouched() {
        // given
        UUID userId = onboardedUser(OWNER_EMAIL, OWNER_NICKNAME);

        // when
        changeNicknameHandler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME));

        // then -> 닉네임 변경이 성별·생일 같은 다른 온보딩 값을 건드리지 않는다
        assertThat(onboardingStore.size()).isEqualTo(1);
        assertThat(onboardingStore.findByUserId(userId)).hasValueSatisfying(onboarding -> {
            assertThat(onboarding.getBirthday().value()).isEqualTo(LocalDate.of(1999, 5, 20));
            assertThat(onboarding.getAvgPace().secondPerKm()).isEqualTo(330);
        });
    }
}
