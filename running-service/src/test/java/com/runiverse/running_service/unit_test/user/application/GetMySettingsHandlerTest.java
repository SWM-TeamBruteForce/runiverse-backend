package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsHandler;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsQuery;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("내 설정 조회 단위 테스트")
public class GetMySettingsHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String INTRODUCTION = "즐겁게 달려요";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @InjectMocks
    private GetMySettingsHandler handler;

    private void givenUser(UUID userId, boolean alertConsent, ProfileVisibility profileVisibility) {
        User user = new User(userId, EMAIL, PASSWORD_HASH, alertConsent,
                null, profileVisibility, INTRODUCTION);
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("저장된 설정을 그대로 돌려준다")
    void returnsStoredSettings() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, true, ProfileVisibility.PUBLIC);

        // when
        GetMySettingsResult result = handler.handle(new GetMySettingsQuery(userId));

        // then -> 두 값 모두 애그리거트가 들고 있어 추가 조회가 없다
        assertThat(result.alertConsent()).isTrue();
        assertThat(result.profileVisibility()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("알림을 끈 사용자는 false로 답한다")
    void returnsAlertConsentOff() {
        // given -> 기본값이 true라 false가 기본값으로 덮이지 않는지 본다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId, false, ProfileVisibility.FRIENDS);

        // when
        GetMySettingsResult result = handler.handle(new GetMySettingsQuery(userId));

        // then
        assertThat(result.alertConsent()).isFalse();
        assertThat(result.profileVisibility()).isEqualTo("FRIENDS");
    }

    @Test
    @DisplayName("사용자가 없으면 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given -> 토큰은 유효하지만 계정이 사라진 경우
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetMySettingsQuery(unknownUserId)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
