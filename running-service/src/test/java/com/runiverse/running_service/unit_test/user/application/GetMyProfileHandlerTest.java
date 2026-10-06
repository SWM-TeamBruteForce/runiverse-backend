package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.LoadOnboardingProfilePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.port.out.OnboardingProfile;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileHandler;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Birthday;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.Height;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.domain.user.vo.Weight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 편집용 조회 단위 테스트")
public class GetMyProfileHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String INTRODUCTION = "즐겁게 달려요";
    private static final LocalDate BIRTHDAY = LocalDate.of(1998, 12, 16);
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private LoadOnboardingProfilePort loadOnboardingProfilePort;

    @InjectMocks
    private GetMyProfileHandler handler;

    private static User userWith(UUID userId, String introduction) {
        return new User(userId, "runner@runiverse.com", PASSWORD_HASH, true,
                null, ProfileVisibility.PUBLIC, introduction);
    }

    private static OnboardingProfile onboardingProfile() {
        return new OnboardingProfile(
                Gender.MALE, new Birthday(BIRTHDAY), new Weight(WEIGHT), new Height(HEIGHT));
    }

    @Test
    @DisplayName("온보딩을 마쳤으면 소개글과 온보딩 값을 함께 반환한다")
    void returnsIntroductionWithOnboardingValues() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(userId)))
                .thenReturn(Optional.of(userWith(userId, INTRODUCTION)));
        when(loadOnboardingProfilePort.loadOnboardingProfile(new UserId(userId)))
                .thenReturn(Optional.of(onboardingProfile()));

        // when
        GetMyProfileResult result = handler.handle(new GetMyProfileQuery(userId));

        // then -> 편집 화면의 입력 칸을 채우는 값이라 프로필 수정과 같은 필드 집합이다
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(result.gender()).isEqualTo("MALE");
        assertThat(result.birthday()).isEqualTo(BIRTHDAY);
        assertThat(result.weightKg()).isEqualTo(WEIGHT);
        assertThat(result.heightCm()).isEqualTo(HEIGHT);
    }

    @Test
    @DisplayName("온보딩 전이면 소개글만 채우고 나머지는 null로 답한다")
    void returnsNullOnboardingValuesBeforeOnboarding() {
        // given -> user_onboardings에 행이 없는 상태다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(userId)))
                .thenReturn(Optional.of(userWith(userId, INTRODUCTION)));
        when(loadOnboardingProfilePort.loadOnboardingProfile(new UserId(userId)))
                .thenReturn(Optional.empty());

        // when
        GetMyProfileResult result = handler.handle(new GetMyProfileQuery(userId));

        // then -> 온보딩 전에도 편집 화면이 열리므로 예외가 아니다
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(result.gender()).isNull();
        assertThat(result.birthday()).isNull();
        assertThat(result.weightKg()).isNull();
        assertThat(result.heightCm()).isNull();
    }

    @Test
    @DisplayName("소개글을 쓴 적 없으면 빈 문자열로 답한다")
    void returnsEmptyIntroductionWhenNeverWritten() {
        // given -> users.introduction이 null이면 어댑터가 ""로 바꿔 올린다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(userId)))
                .thenReturn(Optional.of(userWith(userId, "")));
        when(loadOnboardingProfilePort.loadOnboardingProfile(new UserId(userId)))
                .thenReturn(Optional.of(onboardingProfile()));

        // when
        GetMyProfileResult result = handler.handle(new GetMyProfileQuery(userId));

        // then -> 조회 응답의 "값 없음"은 다른 필드와 같이 null이다
        assertThat(result.introduction()).isNull();
    }

    @Test
    @DisplayName("사용자가 없으면 온보딩을 조회하지 않고 예외를 던진다")
    void throwsWhenUserNotFound() {
        // given -> 토큰은 유효하지만 계정이 남아 있지 않은 경우다
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetMyProfileQuery(unknownUserId)))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(loadOnboardingProfilePort);
    }
}
