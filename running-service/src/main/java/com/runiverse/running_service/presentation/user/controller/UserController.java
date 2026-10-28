package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.running.port.in.GetUserStatusUsecase;
import com.runiverse.running_service.application.running.query.status.GetUserStatusQuery;
import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;
import com.runiverse.running_service.application.user.command.accountdeletion.DeleteAccountCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageResult;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlResult;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameResult;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingResult;
import com.runiverse.running_service.application.user.command.password.ChangePasswordCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileResult;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageResult;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlResult;
import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageCommand;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsCommand;
import com.runiverse.running_service.application.user.command.settings.ChangeMySettingsResult;
import com.runiverse.running_service.application.user.port.in.ChangeBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.in.ChangeMyProfileUsecase;
import com.runiverse.running_service.application.user.port.in.ChangeMySettingsUsecase;
import com.runiverse.running_service.application.user.port.in.ChangeNicknameUsecase;
import com.runiverse.running_service.application.user.port.in.ChangePasswordUsecase;
import com.runiverse.running_service.application.user.port.in.ChangeProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.CheckNicknameAvailabilityUsecase;
import com.runiverse.running_service.application.user.port.in.CompleteOnboardingUsecase;
import com.runiverse.running_service.application.user.port.in.CreateBackgroundImageUploadUrlUsecase;
import com.runiverse.running_service.application.user.port.in.CreateProfileImageUploadUrlUsecase;
import com.runiverse.running_service.application.user.port.in.DeleteAccountUsecase;
import com.runiverse.running_service.application.user.port.in.DeleteBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.in.DeleteProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetMyBasicInfoUsecase;
import com.runiverse.running_service.application.user.port.in.GetMyProfileUsecase;
import com.runiverse.running_service.application.user.port.in.GetMySettingsUsecase;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetUserProfileUsecase;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlQuery;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlResult;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoQuery;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoResult;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityQuery;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityResult;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileResult;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileResult;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsQuery;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsResult;
import com.runiverse.running_service.presentation.user.request.BackgroundImageUpdateRequest;
import com.runiverse.running_service.presentation.user.request.ImageUploadUrlRequest;
import com.runiverse.running_service.presentation.user.request.NicknameAvailabilityRequest;
import com.runiverse.running_service.presentation.user.request.NicknameUpdateRequest;
import com.runiverse.running_service.presentation.user.request.OnboardingRequest;
import com.runiverse.running_service.presentation.user.request.PasswordUpdateRequest;
import com.runiverse.running_service.presentation.user.request.ProfileImageUpdateRequest;
import com.runiverse.running_service.presentation.user.request.ProfileUpdateRequest;
import com.runiverse.running_service.presentation.user.request.SettingsUpdateRequest;
import com.runiverse.running_service.presentation.user.response.BackgroundImageUpdateResponse;
import com.runiverse.running_service.presentation.user.response.BackgroundImageUploadUrlResponse;
import com.runiverse.running_service.presentation.user.response.BackgroundImageUrlResponse;
import com.runiverse.running_service.presentation.user.response.MyBasicInfoResponse;
import com.runiverse.running_service.presentation.user.response.MyProfileResponse;
import com.runiverse.running_service.presentation.user.response.MySettingsResponse;
import com.runiverse.running_service.presentation.user.response.NicknameAvailabilityResponse;
import com.runiverse.running_service.presentation.user.response.NicknameUpdateResponse;
import com.runiverse.running_service.presentation.user.response.OnboardingResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUpdateResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUploadUrlResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUrlResponse;
import com.runiverse.running_service.presentation.user.response.ProfileUpdateResponse;
import com.runiverse.running_service.presentation.user.response.UserProfileResponse;
import com.runiverse.running_service.presentation.user.response.UserStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UserController {

    // UserId VO가 받는 UUIDv7만 — UUID 타입으로 받으면 1-1-1-1-1 같은 값도 변환된다
    private static final String USER_ID_FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final CompleteOnboardingUsecase completeOnboardingUsecase;
    private final GetMyBasicInfoUsecase getMyBasicInfoUsecase;
    private final GetMyProfileUsecase getMyProfileUsecase;
    private final GetUserProfileUsecase getUserProfileUsecase;
    private final ChangeMyProfileUsecase changeMyProfileUsecase;
    private final CreateProfileImageUploadUrlUsecase createProfileImageUploadUrlUsecase;
    private final ChangeProfileImageUsecase changeProfileImageUsecase;
    private final GetProfileImageUsecase getProfileImageUsecase;
    private final DeleteProfileImageUsecase deleteProfileImageUsecase;
    private final CreateBackgroundImageUploadUrlUsecase createBackgroundImageUploadUrlUsecase;
    private final ChangeBackgroundImageUsecase changeBackgroundImageUsecase;
    private final GetBackgroundImageUsecase getBackgroundImageUsecase;
    private final DeleteBackgroundImageUsecase deleteBackgroundImageUsecase;
    private final CheckNicknameAvailabilityUsecase checkNicknameAvailabilityUsecase;
    private final ChangeNicknameUsecase changeNicknameUsecase;
    private final ChangePasswordUsecase changePasswordUsecase;
    private final GetMySettingsUsecase getMySettingsUsecase;
    private final ChangeMySettingsUsecase changeMySettingsUsecase;
    private final DeleteAccountUsecase deleteAccountUsecase;
    private final GetUserStatusUsecase getUserStatusUsecase;

    @PostMapping("/onboarding")
    public ResponseEntity<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OnboardingRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CompleteOnboardingResult result = completeOnboardingUsecase.handle(
                new CompleteOnboardingCommand(
                        userId,
                        request.nickname(),
                        request.gender(),
                        request.birthday(),
                        request.averagePaceSecondsPerKm(),
                        request.weightKg(),
                        request.heightCm()
                ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OnboardingResponse(result.userId(), result.nickname()));
    }

    @GetMapping("/me")
    public ResponseEntity<MyBasicInfoResponse> getMyBasicInfo(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetMyBasicInfoResult result = getMyBasicInfoUsecase.handle(new GetMyBasicInfoQuery(userId));
        return ResponseEntity.ok(new MyBasicInfoResponse(
                result.userId(),
                result.email(),
                result.loginType(),
                result.nickname(),
                result.isOnboarded()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        UUID viewerId = UUID.fromString(jwt.getSubject());
        GetUserProfileResult result = getUserProfileUsecase.handle(
                new GetUserProfileQuery(viewerId, UUID.fromString(userId)));
        return ResponseEntity.ok(new UserProfileResponse(
                result.userId(),
                result.isMe(),
                result.nickname(),
                result.profileImageUrl(),
                result.introduction(),
                result.friendCount(),
                result.relationshipType()
        ));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<MyProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetMyProfileResult result = getMyProfileUsecase.handle(new GetMyProfileQuery(userId));
        return ResponseEntity.ok(new MyProfileResponse(
                result.introduction(),
                result.gender(),
                result.birthday(),
                result.weightKg(),
                result.heightCm()
        ));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<ProfileUpdateResponse> changeMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChangeMyProfileResult result = changeMyProfileUsecase.handle(new ChangeMyProfileCommand(
                userId,
                request.introduction(),
                request.gender(),
                request.birthday(),
                request.weightKg(),
                request.heightCm()
        ));
        return ResponseEntity.ok(new ProfileUpdateResponse(
                result.introduction(),
                result.gender(),
                result.birthday(),
                result.weightKg(),
                result.heightCm()
        ));
    }

    @PostMapping("/me/profile-image/presigned-url")
    public ResponseEntity<ProfileImageUploadUrlResponse> createProfileImageUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ImageUploadUrlRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CreateProfileImageUploadUrlResult result = createProfileImageUploadUrlUsecase.handle(
                new CreateProfileImageUploadUrlCommand(userId, request.mimeType(), request.fileSizeBytes())
        );
        return ResponseEntity.ok(
                new ProfileImageUploadUrlResponse(result.profileImageKey(), result.uploadUrl()));
    }

    @PatchMapping("/me/profile-image")
    public ResponseEntity<ProfileImageUpdateResponse> changeProfileImage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileImageUpdateRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChangeProfileImageResult result = changeProfileImageUsecase.handle(
                new ChangeProfileImageCommand(userId, request.profileImageKey()));
        return ResponseEntity.ok(new ProfileImageUpdateResponse(result.profileImageKey()));
    }

    @GetMapping("/{userId}/profile-image")
    public ResponseEntity<ProfileImageUrlResponse> getProfileImageUrl(
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        GetProfileImageUrlResult result = getProfileImageUsecase.handle(
                new GetProfileImageUrlQuery(UUID.fromString(userId)));
        return ResponseEntity.ok(new ProfileImageUrlResponse(result.profileImageUrl()));
    }

    @DeleteMapping("/me/profile-image")
    public ResponseEntity<Void> deleteProfileImage(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        deleteProfileImageUsecase.handle(new DeleteProfileImageCommand(userId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/background-image/presigned-url")
    public ResponseEntity<BackgroundImageUploadUrlResponse> createBackgroundImageUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ImageUploadUrlRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CreateBackgroundImageUploadUrlResult result = createBackgroundImageUploadUrlUsecase.handle(
                new CreateBackgroundImageUploadUrlCommand(userId, request.mimeType(), request.fileSizeBytes())
        );
        return ResponseEntity.ok(
                new BackgroundImageUploadUrlResponse(result.backgroundImageKey(), result.uploadUrl()));
    }

    @PatchMapping("/me/background-image")
    public ResponseEntity<BackgroundImageUpdateResponse> changeBackgroundImage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BackgroundImageUpdateRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChangeBackgroundImageResult result = changeBackgroundImageUsecase.handle(
                new ChangeBackgroundImageCommand(userId, request.backgroundImageKey()));
        return ResponseEntity.ok(new BackgroundImageUpdateResponse(result.backgroundImageKey()));
    }

    @GetMapping("/{userId}/background-image")
    public ResponseEntity<BackgroundImageUrlResponse> getBackgroundImageUrl(
            @PathVariable @Pattern(regexp = USER_ID_FORMAT, message = "입력값이 올바르지 않습니다.") String userId
    ) {
        GetBackgroundImageUrlResult result = getBackgroundImageUsecase.handle(
                new GetBackgroundImageUrlQuery(UUID.fromString(userId)));
        return ResponseEntity.ok(new BackgroundImageUrlResponse(result.backgroundImageUrl()));
    }

    @DeleteMapping("/me/background-image")
    public ResponseEntity<Void> deleteBackgroundImage(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        deleteBackgroundImageUsecase.handle(new DeleteBackgroundImageCommand(userId));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<NicknameUpdateResponse> changeNickname(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChangeNicknameResult result = changeNicknameUsecase.handle(new ChangeNicknameCommand(userId,
                request.nickname()));
        return ResponseEntity.ok(new NicknameUpdateResponse(result.nickname()));
    }

    @PostMapping("/nickname/availability")
    public ResponseEntity<NicknameAvailabilityResponse> checkNicknameAvailability(
            @Valid @RequestBody NicknameAvailabilityRequest request
    ) {
        CheckNicknameAvailabilityResult result = checkNicknameAvailabilityUsecase.handle(
                new CheckNicknameAvailabilityQuery(request.nickname()));
        return ResponseEntity.ok(
                new NicknameAvailabilityResponse(result.nickname(), result.available()));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordUpdateRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        changePasswordUsecase.handle(new ChangePasswordCommand(
                userId,
                request.currentPassword(),
                request.newPassword()
        ));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/settings")
    public ResponseEntity<MySettingsResponse> getMySettings(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetMySettingsResult result = getMySettingsUsecase.handle(new GetMySettingsQuery(userId));
        return ResponseEntity.ok(
                new MySettingsResponse(result.alertConsent(), result.profileVisibility()));
    }

    @PatchMapping("/me/settings")
    public ResponseEntity<MySettingsResponse> changeMySettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SettingsUpdateRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChangeMySettingsResult result = changeMySettingsUsecase.handle(new ChangeMySettingsCommand(
                userId,
                request.alertConsent(),
                request.profileVisibility()
        ));
        return ResponseEntity.ok(
                new MySettingsResponse(result.alertConsent(), result.profileVisibility()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        deleteAccountUsecase.handle(new DeleteAccountCommand(
                UUID.fromString(jwt.getSubject()),
                jwt.getId()
        ));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/status")
    public ResponseEntity<UserStatusResponse> getMyStatus(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetUserStatusResult result = getUserStatusUsecase.handle(new GetUserStatusQuery(userId));
        return ResponseEntity.ok(new UserStatusResponse(
                result.status(),
                result.type(),
                result.runningRoomId(),
                result.scheduledStartAt(),
                result.targetDistanceMeters(),
                result.cooldownUntil()));
    }
}
