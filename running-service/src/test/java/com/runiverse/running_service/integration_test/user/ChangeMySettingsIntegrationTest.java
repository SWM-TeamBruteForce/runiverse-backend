package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsCommand;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsHandler;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsResult;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsHandler;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsQuery;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsResult;
import com.runiverse.running_service.domain.user.exception.UnsupportedProfileVisibilityException;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("내 설정 조회·변경 통합 테스트")
public class ChangeMySettingsIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "runner@runiverse.com";
    private static final String OTHER_EMAIL = "other@runiverse.com";
    private static final String PASSWORD = "Password123!";

    private SignUpHandler signUpHandler;
    private GetMySettingsHandler getMySettingsHandler;
    private ChangeMySettingsHandler changeMySettingsHandler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        getMySettingsHandler = new GetMySettingsHandler(
                userStore  // LoadUserByIdPort
        );
        changeMySettingsHandler = new ChangeMySettingsHandler(
                userStore,  // LoadUserByIdPort
                userStore   // UpdateSettingsPort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    private GetMySettingsResult settingsOf(UUID userId) {
        return getMySettingsHandler.handle(new GetMySettingsQuery(userId));
    }

    private ChangeMySettingsResult change(UUID userId, Boolean alertConsent, String visibility) {
        return changeMySettingsHandler.handle(
                new ChangeMySettingsCommand(userId, alertConsent, visibility));
    }

    @Test
    @DisplayName("가입 직후에는 알림이 켜져 있고 전체 공개다")
    void startsWithDefaultSettings() {
        // given -> 가입 시점의 기본값을 그대로 읽는다
        UUID userId = signUp(EMAIL);

        // when
        GetMySettingsResult result = settingsOf(userId);

        // then
        assertThat(result.alertConsent()).isTrue();
        assertThat(result.profileVisibility()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("알림만 꺼도 공개 범위까지 담아 돌려주고 다시 조회해도 같다")
    void turnsOffAlertConsentAndKeepsVisibility() {
        // given
        UUID userId = signUp(EMAIL);

        // when
        ChangeMySettingsResult changed = change(userId, false, null);

        // then -> 응답이 곧 저장된 설정이라 조회 결과와 같아야 한다
        assertThat(changed.alertConsent()).isFalse();
        assertThat(changed.profileVisibility()).isEqualTo("PUBLIC");
        assertThat(settingsOf(userId).alertConsent()).isFalse();
        assertThat(settingsOf(userId).profileVisibility()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("공개 범위만 바꿔도 앞서 끈 알림이 되살아나지 않는다")
    void changingVisibilityKeepsPreviousAlertConsent() {
        // given -> 토글을 하나씩 눌러 두 번 요청한 경우다
        UUID userId = signUp(EMAIL);
        change(userId, false, null);

        // when
        ChangeMySettingsResult changed = change(userId, null, "FRIENDS");

        // then -> 보내지 않은 필드를 기본값으로 덮으면 안 된다
        assertThat(changed.alertConsent()).isFalse();
        assertThat(changed.profileVisibility()).isEqualTo("FRIENDS");
        assertThat(settingsOf(userId).alertConsent()).isFalse();
    }

    @Test
    @DisplayName("빈 요청은 아무것도 바꾸지 않고 현재 설정을 돌려준다")
    void emptyRequestChangesNothing() {
        // given
        UUID userId = signUp(EMAIL);
        change(userId, false, "FRIENDS");

        // when
        ChangeMySettingsResult changed = change(userId, null, null);

        // then
        assertThat(changed.alertConsent()).isFalse();
        assertThat(changed.profileVisibility()).isEqualTo("FRIENDS");
    }

    @Test
    @DisplayName("지원하지 않는 공개 범위는 저장하지 않는다")
    void rejectsUnsupportedVisibility() {
        // given
        UUID userId = signUp(EMAIL);

        // when & then -> 함께 보낸 알림도 바뀌지 않아야 한다
        assertThatThrownBy(() -> change(userId, false, "PRIVATE"))
                .isInstanceOf(UnsupportedProfileVisibilityException.class);
        assertThat(settingsOf(userId).alertConsent()).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 설정은 함께 바뀌지 않는다")
    void otherUsersSettingsAreUntouched() {
        // given -> 두 계정이 각각 설정을 갖는다
        UUID userId = signUp(EMAIL);
        UUID otherUserId = signUp(OTHER_EMAIL);

        // when
        change(userId, false, "FRIENDS");

        // then
        assertThat(settingsOf(otherUserId).alertConsent()).isTrue();
        assertThat(settingsOf(otherUserId).profileVisibility()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 설정을 바꿀 수 없다")
    void throwsForUnknownUser() {
        // when & then -> 토큰은 유효하지만 계정이 없는 경우다
        assertThatThrownBy(() -> change(UuidCreator.getTimeOrderedEpoch(), false, null))
                .isInstanceOf(UserNotFoundException.class);
    }
}
