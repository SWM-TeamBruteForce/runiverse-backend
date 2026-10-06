package com.runiverse.running_service.presentation.auth.controller;

import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationCommand;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeCommand;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeResult;
import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.logout.LogoutCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginResult;
import com.runiverse.running_service.application.auth.command.refresh.RefreshCommand;
import com.runiverse.running_service.application.auth.command.refresh.RefreshResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.port.in.LoginUsecase;
import com.runiverse.running_service.application.auth.port.in.LogoutUsecase;
import com.runiverse.running_service.application.auth.port.in.OauthLoginUsecase;
import com.runiverse.running_service.application.auth.port.in.RefreshUsecase;
import com.runiverse.running_service.application.auth.port.in.SendEmailVerificationUsecase;
import com.runiverse.running_service.application.auth.port.in.SignUpUsecase;
import com.runiverse.running_service.application.auth.port.in.VerifyEmailCodeUsecase;
import com.runiverse.running_service.presentation.auth.request.EmailVerificationRequest;
import com.runiverse.running_service.presentation.auth.request.GoogleLoginRequest;
import com.runiverse.running_service.presentation.auth.request.KakaoLoginRequest;
import com.runiverse.running_service.presentation.auth.request.LoginRequest;
import com.runiverse.running_service.presentation.auth.request.RefreshRequest;
import com.runiverse.running_service.presentation.auth.request.SignUpRequest;
import com.runiverse.running_service.presentation.auth.request.VerifyEmailCodeRequest;
import com.runiverse.running_service.presentation.auth.response.LoginResponse;
import com.runiverse.running_service.presentation.auth.response.OauthLoginResponse;
import com.runiverse.running_service.presentation.auth.response.RefreshResponse;
import com.runiverse.running_service.presentation.auth.response.SignUpResponse;
import com.runiverse.running_service.presentation.auth.response.VerifyEmailCodeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignUpUsecase signUpUsecase;
    private final SendEmailVerificationUsecase sendEmailVerificationUsecase;
    private final VerifyEmailCodeUsecase verifyEmailCodeUsecase;
    private final LoginUsecase loginUsecase;
    private final LogoutUsecase logoutUsecase;
    private final OauthLoginUsecase oauthLoginUsecase;
    private final RefreshUsecase refreshUsecase;

    @PostMapping("/email/verifications")
    public ResponseEntity<Void> sendEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        SendEmailVerificationCommand command = new SendEmailVerificationCommand(request.email());
        sendEmailVerificationUsecase.handle(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/verifications/confirm")
    public ResponseEntity<VerifyEmailCodeResponse> verifyEmailCode(
            @Valid @RequestBody VerifyEmailCodeRequest request
    ) {
        VerifyEmailCodeCommand command = new VerifyEmailCodeCommand(
                request.email(),
                request.code()
        );
        VerifyEmailCodeResult result = verifyEmailCodeUsecase.handle(command);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new VerifyEmailCodeResponse(result.verificationTicket()));
    }

    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(
            @Valid @RequestBody SignUpRequest request
    ) {

        SignUpCommand command = new SignUpCommand(
                request.verificationTicket(),
                request.password()
        );

        SignUpResult result = signUpUsecase.handle(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignUpResponse(
                        result.userId(),
                        result.accessToken(),
                        result.refreshToken()
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        LoginCommand command = new LoginCommand(
                request.email(),
                request.password()
        );

        LoginResult result = loginUsecase.handle(command);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new LoginResponse(
                        result.userId(),
                        result.accessToken(),
                        result.refreshToken()
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt
    ) {
        LogoutCommand command = new LogoutCommand(
                UUID.fromString(jwt.getSubject()),
                jwt.getId()
        );
        logoutUsecase.handle(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<OauthLoginResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request
    ) {
        return oauthLogin(new OauthLoginCommand(
                "google",
                request.idToken(),
                null,
                null
        ));
    }

    @PostMapping("/oauth/kakao")
    public ResponseEntity<OauthLoginResponse> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        return oauthLogin(new OauthLoginCommand(
                "kakao",
                null,
                request.authorizationCode(),
                request.codeVerifier()
        ));
    }

    // 목록에 없는 provider — 고정 경로가 더 구체적이라 먼저 매칭되므로 google·kakao는 여기로 오지 않는다.
    // 본문은 읽지 않는다: provider마다 본문 모양이 달라 검증할 기준이 없다
    @PostMapping("/oauth/{provider}")
    public ResponseEntity<OauthLoginResponse> unsupportedOauthLogin(
            @PathVariable String provider
    ) {
        return oauthLogin(new OauthLoginCommand(provider, null, null, null));
    }

    private ResponseEntity<OauthLoginResponse> oauthLogin(OauthLoginCommand command) {
        OauthLoginResult result = oauthLoginUsecase.handle(command);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new OauthLoginResponse(
                        result.userId(),
                        result.accessToken(),
                        result.refreshToken()
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        RefreshCommand command = new RefreshCommand(
                request.refreshToken()
        );
        RefreshResult result = refreshUsecase.handle(command);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new RefreshResponse(
                        result.accessToken(),
                        result.refreshToken()
                ));
    }
}
