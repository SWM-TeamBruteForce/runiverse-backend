package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsCommand;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsHandler;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsResult;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.UpdateSettingsPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.exception.UnsupportedProfileVisibilityException;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("내 설정 변경 단위 테스트")
public class ChangeMySettingsHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String INTRODUCTION = "즐겁게 달려요";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private UpdateSettingsPort updateSettingsPort;

    @InjectMocks
    private ChangeMySettingsHandler handler;

    private void givenUser(UUID userId, boolean alertConsent, ProfileVisibility profileVisibility) {
        User user = new User(userId, EMAIL, PASSWORD_HASH, alertConsent,
                null, profileVisibility, INTRODUCTION);
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("알림만 바꿔도 공개 범위까지 담아 돌려준다")
    void returnsWholeSettingsWhenOnlyAlertConsentGiven() {
        // given -> 설정 화면이 토글 하나만 눌러 보내는 경우다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, true, ProfileVisibility.PUBLIC);

        // when
        ChangeMySettingsResult result = handler.handle(
                new ChangeMySettingsCommand(userId, false, null));

        // then -> 보내지 않은 값은 갱신 전 값으로 채운다
        assertThat(result.alertConsent()).isFalse();
        assertThat(result.profileVisibility()).isEqualTo("PUBLIC");
        verify(updateSettingsPort).updateSettings(new UserId(userId), false, null);
    }

    @Test
    @DisplayName("공개 범위만 바꿔도 알림까지 담아 돌려준다")
    void returnsWholeSettingsWhenOnlyVisibilityGiven() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, true, ProfileVisibility.PUBLIC);

        // when
        ChangeMySettingsResult result = handler.handle(
                new ChangeMySettingsCommand(userId, null, "FRIENDS"));

        // then
        assertThat(result.alertConsent()).isTrue();
        assertThat(result.profileVisibility()).isEqualTo("FRIENDS");
        verify(updateSettingsPort)
                .updateSettings(new UserId(userId), null, ProfileVisibility.FRIENDS);
    }

    @Test
    @DisplayName("두 값을 함께 보내면 둘 다 갱신한다")
    void updatesBothFields() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, true, ProfileVisibility.PUBLIC);

        // when
        ChangeMySettingsResult result = handler.handle(
                new ChangeMySettingsCommand(userId, false, "FRIENDS"));

        // then
        assertThat(result.alertConsent()).isFalse();
        assertThat(result.profileVisibility()).isEqualTo("FRIENDS");
        verify(updateSettingsPort)
                .updateSettings(new UserId(userId), false, ProfileVisibility.FRIENDS);
    }

    @Test
    @DisplayName("소문자로 보내도 대문자 값으로 갱신한다")
    void normalizesLowerCaseVisibility() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, true, ProfileVisibility.PUBLIC);

        // when
        ChangeMySettingsResult result = handler.handle(
                new ChangeMySettingsCommand(userId, null, "friends"));

        // then -> 저장 값은 enum 이름 하나로 통일한다
        assertThat(result.profileVisibility()).isEqualTo("FRIENDS");
        verify(updateSettingsPort)
                .updateSettings(new UserId(userId), null, ProfileVisibility.FRIENDS);
    }

    @Test
    @DisplayName("바꿀 값이 없으면 저장하지 않고 현재 설정을 돌려준다")
    void returnsCurrentSettingsWhenNothingGiven() {
        // given -> 빈 요청이라 갱신할 것이 없다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, false, ProfileVisibility.FRIENDS);

        // when
        ChangeMySettingsResult result = handler.handle(
                new ChangeMySettingsCommand(userId, null, null));

        // then -> 아무것도 바꾸지 않지만 응답은 조회와 같은 형식이다
        assertThat(result.alertConsent()).isFalse();
        assertThat(result.profileVisibility()).isEqualTo("FRIENDS");
        verifyNoInteractions(updateSettingsPort);
    }

    @Test
    @DisplayName("지원하지 않는 공개 범위면 아무것도 바꾸지 않고 막는다")
    void rejectsUnsupportedVisibilityBeforeAnyUpdate() {
        // given -> 알림은 통과하지만 공개 범위가 허용 값이 아닌 요청이다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, true, ProfileVisibility.PUBLIC);

        // when & then -> VO를 먼저 만들어야 절반만 저장되지 않는다
        assertThatThrownBy(() -> handler.handle(
                new ChangeMySettingsCommand(userId, false, "PRIVATE")))
                .isInstanceOf(UnsupportedProfileVisibilityException.class);
        verifyNoInteractions(updateSettingsPort);
    }

    @Test
    @DisplayName("사용자가 없으면 갱신하지 않고 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given -> 토큰은 유효하지만 계정이 사라진 경우
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new ChangeMySettingsCommand(unknownUserId, false, null)))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(updateSettingsPort);
    }
}
