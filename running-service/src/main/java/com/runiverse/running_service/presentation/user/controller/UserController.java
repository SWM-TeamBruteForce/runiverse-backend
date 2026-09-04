package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileCommand;
import com.runiverse.running_service.application.user.command.profile.ChangeMyProfileResult;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameResult;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingResult;
import com.runiverse.running_service.application.user.command.password.ChangePasswordCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageResult;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlResult;
import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageCommand;
import com.runiverse.running_service.application.user.port.in.ChangeNicknameUsecase;
import com.runiverse.running_service.application.user.port.in.ChangePasswordUsecase;
import com.runiverse.running_service.application.user.port.in.ChangeProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.ChangeMyProfileUsecase;
import com.runiverse.running_service.application.user.port.in.CheckNicknameAvailabilityUsecase;
import com.runiverse.running_service.application.user.port.in.CompleteOnboardingUsecase;
import com.runiverse.running_service.application.user.port.in.CreateProfileImageUploadUrlUsecase;
import com.runiverse.running_service.application.user.port.in.DeleteProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetMyAccountUsecase;
import com.runiverse.running_service.application.user.port.in.GetMyBasicInfoUsecase;
import com.runiverse.running_service.application.user.port.in.GetMyProfileUsecase;
import com.runiverse.running_service.application.user.port.in.GetUserProfileUsecase;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityQuery;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityResult;
import com.runiverse.running_service.application.user.query.account.GetMyAccountQuery;
import com.runiverse.running_service.application.user.query.account.GetMyAccountResult;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoQuery;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoResult;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileResult;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileResult;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;
import com.runiverse.running_service.presentation.user.request.NicknameAvailabilityRequest;
import com.runiverse.running_service.presentation.user.request.NicknameUpdateRequest;
import com.runiverse.running_service.presentation.user.request.OnboardingRequest;
import com.runiverse.running_service.presentation.user.request.PasswordUpdateRequest;
import com.runiverse.running_service.presentation.user.request.ProfileImageUpdateRequest;
import com.runiverse.running_service.presentation.user.request.ProfileImageUploadUrlRequest;
import com.runiverse.running_service.presentation.user.request.ProfileUpdateRequest;
import com.runiverse.running_service.presentation.user.response.NicknameAvailabilityResponse;
import com.runiverse.running_service.presentation.user.response.NicknameUpdateResponse;
import com.runiverse.running_service.presentation.user.response.OnboardingResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUpdateResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUploadUrlResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUrlResponse;
import com.runiverse.running_service.presentation.user.response.MyAccountResponse;
import com.runiverse.running_service.presentation.user.response.MyBasicInfoResponse;
import com.runiverse.running_service.presentation.user.response.MyProfileResponse;
import com.runiverse.running_service.presentation.user.response.UserProfileResponse;
import com.runiverse.running_service.presentation.user.response.ProfileUpdateResponse;
import jakarta.validation.Valid;
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

    private final CompleteOnboardingUsecase completeOnboardingUsecase;
    private final GetMyBasicInfoUsecase getMyBasicInfoUsecase;
    private final GetMyProfileUsecase getMyProfileUsecase;
    private final GetUserProfileUsecase getUserProfileUsecase;
    private final ChangeMyProfileUsecase changeMyProfileUsecase;
    private final CreateProfileImageUploadUrlUsecase createProfileImageUploadUrlUsecase;
    private final ChangeProfileImageUsecase changeProfileImageUsecase;
    private final GetProfileImageUsecase getProfileImageUsecase;
    private final DeleteProfileImageUsecase deleteProfileImageUsecase;
    private final CheckNicknameAvailabilityUsecase checkNicknameAvailabilityUsecase;
    private final ChangeNicknameUsecase changeNicknameUsecase;
    private final GetMyAccountUsecase getMyAccountUsecase;
    private final ChangePasswordUsecase changePasswordUsecase;

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
        return ResponseEntity.ok(
                new MyBasicInfoResponse(result.userId(), result.nickname(), result.isOnboarded()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        UUID viewerId = UUID.fromString(jwt.getSubject());
        GetUserProfileResult result = getUserProfileUsecase.handle(
                new GetUserProfileQuery(viewerId, userId));
        return ResponseEntity.ok(new UserProfileResponse(
                result.userId(),
                result.isMe(),
                result.nickname(),
                result.profileImageUrl(),
                result.introduction(),
                result.friendCount(),
                result.friendStatus()
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
            @Valid @RequestBody ProfileImageUploadUrlRequest request
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
    public ResponseEntity<ProfileImageUrlResponse> getProfileImageUrl(@PathVariable UUID userId) {
        GetProfileImageUrlResult result = getProfileImageUsecase.handle(
                new GetProfileImageUrlQuery(userId));
        return ResponseEntity.ok(new ProfileImageUrlResponse(result.profileImageUrl()));
    }

    @DeleteMapping("/me/profile-image")
    public ResponseEntity<Void> deleteProfileImage(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        deleteProfileImageUsecase.handle(new DeleteProfileImageCommand(userId));
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

    @GetMapping("/me/account")
    public ResponseEntity<MyAccountResponse> getMyAccount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GetMyAccountResult result = getMyAccountUsecase.handle(new GetMyAccountQuery(userId));
        return ResponseEntity.ok(new MyAccountResponse(result.email(), result.loginType()));
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
}
