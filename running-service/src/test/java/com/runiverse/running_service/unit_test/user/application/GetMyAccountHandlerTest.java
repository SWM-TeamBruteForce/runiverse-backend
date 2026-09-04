package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadOauthProviderPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.query.account.GetMyAccountHandler;
import com.runiverse.running_service.application.user.query.account.GetMyAccountQuery;
import com.runiverse.running_service.application.user.query.account.GetMyAccountResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.aggregate.User;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.domain.user.vo.Provider;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("내 계정 정보 조회 단위 테스트")
public class GetMyAccountHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String EMAIL = "runner@runiverse.com";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private LoadOauthProviderPort loadOauthProviderPort;

    @InjectMocks
    private GetMyAccountHandler handler;

    private static User userOf(UUID userId) {
        return new User(userId, EMAIL, PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, "즐겁게 달려요");
    }

    private void givenUser(UUID userId) {
        when(loadUserByIdPort.loadById(new UserId(userId)))
                .thenReturn(Optional.of(userOf(userId)));
    }

    private void givenProvider(UUID userId, Provider provider) {
        when(loadOauthProviderPort.loadProvider(new UserId(userId)))
                .thenReturn(Optional.of(provider));
    }

    private void givenNoProvider(UUID userId) {
        when(loadOauthProviderPort.loadProvider(new UserId(userId)))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("소셜 연동이 없으면 LOCAL로 답한다")
    void reportsLocalWithoutOauth() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenNoProvider(userId);

        // when
        GetMyAccountResult result = handler.handle(new GetMyAccountQuery(userId));

        // then -> 비밀번호 유무가 아니라 oauth_users 행 유무로 가른다
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.loginType()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("구글로 가입했으면 GOOGLE로 답한다")
    void reportsGoogleForGoogleUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenProvider(userId, Provider.GOOGLE);

        // when
        GetMyAccountResult result = handler.handle(new GetMyAccountQuery(userId));

        // then
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.loginType()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("카카오로 가입했으면 KAKAO로 답한다")
    void reportsKakaoForKakaoUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenProvider(userId, Provider.KAKAO);

        // when
        GetMyAccountResult result = handler.handle(new GetMyAccountQuery(userId));

        // then
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.loginType()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("사용자가 없으면 로그인 수단을 조회하지 않고 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given -> 토큰은 유효하지만 계정이 사라진 경우
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetMyAccountQuery(unknownUserId)))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(loadOauthProviderPort);
    }
}
