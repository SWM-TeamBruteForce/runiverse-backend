package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadOauthProviderPort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoHandler;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoQuery;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Nickname;
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
@DisplayName("내 기본 정보 조회 단위 테스트")
public class GetMyBasicInfoHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String NICKNAME = "러너킴";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private LoadNicknamePort loadNicknamePort;

    @Mock
    private LoadOauthProviderPort loadOauthProviderPort;

    @InjectMocks
    private GetMyBasicInfoHandler handler;

    private static User userOf(UUID userId) {
        return new User(userId, EMAIL, PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, "즐겁게 달려요");
    }

    private void givenUser(UUID userId) {
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.of(userOf(userId)));
    }

    private void givenNickname(UUID userId) {
        when(loadNicknamePort.loadNickname(new UserId(userId)))
                .thenReturn(Optional.of(new Nickname(NICKNAME)));
    }

    private void givenNoNickname(UUID userId) {
        when(loadNicknamePort.loadNickname(new UserId(userId))).thenReturn(Optional.empty());
    }

    private void givenProvider(UUID userId, Provider provider) {
        when(loadOauthProviderPort.loadProvider(new UserId(userId)))
                .thenReturn(Optional.of(provider));
    }

    private void givenNoProvider(UUID userId) {
        when(loadOauthProviderPort.loadProvider(new UserId(userId))).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("온보딩을 마친 사용자는 닉네임과 완료 여부를 함께 반환한다")
    void returnsNicknameForOnboardedUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenNickname(userId);
        givenNoProvider(userId);

        // when
        GetMyBasicInfoResult result = handler.handle(new GetMyBasicInfoQuery(userId));

        // then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(result.isOnboarded()).isTrue();
    }

    @Test
    @DisplayName("가입만 하고 온보딩 전이면 닉네임 없이 미완료로 답한다")
    void reportsNotOnboardedBeforeOnboarding() {
        // given -> 닉네임은 user_onboardings에 있어 온보딩 전에는 행 자체가 없다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenNoNickname(userId);
        givenNoProvider(userId);

        // when
        GetMyBasicInfoResult result = handler.handle(new GetMyBasicInfoQuery(userId));

        // then -> 온보딩 전은 정상 상태다. 앱은 이 값으로 홈과 온보딩 화면을 가른다
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.nickname()).isNull();
        assertThat(result.isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("소셜 연동이 없으면 LOCAL로 답한다")
    void reportsLocalWithoutOauth() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenNickname(userId);
        givenNoProvider(userId);

        // when
        GetMyBasicInfoResult result = handler.handle(new GetMyBasicInfoQuery(userId));

        // then -> 비밀번호 유무가 아니라 oauth_users 행 유무로 가른다
        assertThat(result.loginType()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("구글로 가입했으면 GOOGLE로 답한다")
    void reportsGoogleForGoogleUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenNickname(userId);
        givenProvider(userId, Provider.GOOGLE);

        // when
        GetMyBasicInfoResult result = handler.handle(new GetMyBasicInfoQuery(userId));

        // then
        assertThat(result.loginType()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("카카오로 가입했으면 KAKAO로 답한다")
    void reportsKakaoForKakaoUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        givenUser(userId);
        givenNickname(userId);
        givenProvider(userId, Provider.KAKAO);

        // when
        GetMyBasicInfoResult result = handler.handle(new GetMyBasicInfoQuery(userId));

        // then
        assertThat(result.loginType()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("사용자가 없으면 닉네임과 로그인 수단을 조회하지 않고 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given -> 토큰은 유효하지만 계정이 사라진 경우
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetMyBasicInfoQuery(unknownUserId)))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(loadNicknamePort, loadOauthProviderPort);
    }
}
