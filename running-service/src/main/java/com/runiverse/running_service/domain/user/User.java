package com.runiverse.running_service.domain.user;

import com.runiverse.running_service.domain.user.exception.LastSignInMethodException;
import com.runiverse.running_service.domain.user.exception.OauthAlreadyLinkedException;
import com.runiverse.running_service.domain.user.exception.OauthNotLinkedException;
import com.runiverse.running_service.domain.user.exception.OnboardingAlreadyCompletedException;
import com.runiverse.running_service.domain.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.user.exception.PasswordHashRequiredException;
import com.runiverse.running_service.domain.user.exception.PasswordNotSetException;
import com.runiverse.running_service.domain.user.exception.ProfileVisibilityRequiredException;
import com.runiverse.running_service.domain.user.vo.Email;
import com.runiverse.running_service.domain.user.vo.Introduction;
import com.runiverse.running_service.domain.user.vo.PasswordHash;
import com.runiverse.running_service.domain.user.vo.ProfileImageKey;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Getter
public class User {

    private final UserId userId;
    private final Email email;
    private PasswordHash passwordHash;
    private final boolean alertConsent;
    private final ProfileVisibility profileVisibility;
    private final ProfileImageKey profileImageKey;
    private final Introduction introduction;

    // 내부 저장
    private OauthUser oauthUser;

    // 유저 온보드
    private UserOnboarding onboarding;

    // 생성자 부분 작성
    public User(UUID userId, String email, String passwordHash, boolean alertConsent, String profileImageKey,
                ProfileVisibility profileVisibility, String introduction) {
        this.userId = new UserId(userId);
        this.email = new Email(email);
        this.passwordHash = new PasswordHash(passwordHash);
        this.alertConsent = alertConsent;
        if (profileVisibility == null) {   // 값 변환은 application에서 검증
            throw new ProfileVisibilityRequiredException();
        }
        this.profileImageKey = profileImageKey == null ? null : new ProfileImageKey(profileImageKey);
        this.profileVisibility = profileVisibility;
        this.introduction = new Introduction(introduction);
    }

    // 로컬 회원가입 할때 사용하는 생성자
    public User(UUID userId, String email, String passwordHash, boolean alertConsent) {
        this(userId, email, passwordHash, alertConsent, null, ProfileVisibility.PUBLIC, "");
    }

    // alertConsent, introduction이 없는 경우 — 알림은 기본 수신
    public User(UUID userId, String email, String passwordHash) {
        this(userId, email, passwordHash, true, null, ProfileVisibility.PUBLIC, "");
    }

    // 로컬 로그인 비밀번호가 없는 계정인지 — OAuth 전용 계정은 빈 해시를 갖는다
    public boolean isPasswordNotSet() {
        return passwordHash.value().isEmpty();
    }

    // 비밀번호 변경 - 해싱은 인프라에서 즉 변경된걸 적용한다. -> 도메인만 책임
    public void changePassword(String newPasswordHash) {
        if (isPasswordNotSet()) {
            throw new PasswordNotSetException();
        }
        PasswordHash changed = new PasswordHash(newPasswordHash);
        if (changed.value().isEmpty()) {
            throw new PasswordHashRequiredException();
        }
        this.passwordHash = changed;
    }

    // oauth로 회원가입 할때 사용하는 생성자
    public User(UUID userId, String email) {
        this(userId, email, "", true, null, ProfileVisibility.PUBLIC, "");
    }

    public Optional<ProfileImageKey> getProfileImageKey() {
        return Optional.ofNullable(profileImageKey);
    }

    // 소셜 회원가입: 유저 생성과 연결을 진행
    public static User registerWithOauth(UUID userId, String email, Provider provider, String providerId) {
        User user = new User(userId, email); // oauth용 유저 생성
        user.linkOauth(provider, providerId);
        return user;
    }

    // oauth 연결
    public void linkOauth(Provider provider, String providerId) {
        if (oauthUser != null) {
            throw new OauthAlreadyLinkedException();   // 하나의
        }
        oauthUser = new OauthUser(userId, provider, providerId);
    }

    // oauth와 연결 끊기
    public void unlinkOauth(Provider provider) {
        if (!hasProvider(provider)) {
            throw new OauthNotLinkedException();
        }
        if (isLastSignInMethod()) {
            throw new LastSignInMethodException();    // I3
        }
        oauthUser = null;
    }

    public boolean hasProvider(Provider provider) {
        return oauthUser != null && oauthUser.isSameProvider(provider);
    }

    public Optional<OauthUser> getOauthUser() {
        return Optional.ofNullable(oauthUser);
    }

    private boolean isLastSignInMethod() {
        return isPasswordNotSet();
    }

    public void completeOnboarding(String nickname, String gender, LocalDate birthday,
                                   int avgPace, BigDecimal weight, BigDecimal height) {
        if (onboarding != null) {
            throw new OnboardingAlreadyCompletedException();
        }
        this.onboarding = new UserOnboarding(userId, nickname, gender, birthday, avgPace, weight, height);
    }

    public void updateOnboarding(String nickname, String gender, LocalDate birthday,
                                 Integer avgPace, BigDecimal weight, BigDecimal height) {
        if (onboarding == null) {
            throw new OnboardingNotCompletedException();
        }
        this.onboarding = onboarding.change(nickname, gender, birthday, avgPace, weight, height);
    }

    public boolean hasOnboarded() {
        return onboarding != null;
    }

    public Optional<UserOnboarding> getOnboarding() {
        return Optional.ofNullable(onboarding);
    }
}
