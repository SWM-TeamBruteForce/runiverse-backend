package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.exception.UnsupportedProviderException;
import com.runiverse.running_service.application.auth.port.in.OauthLoginUsecase;
import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.LoadGoogleProfilePort;
import com.runiverse.running_service.application.auth.port.out.LoadKakaoProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.auth.port.out.RecordAuthMetricPort;
import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenHashPort;
import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.exception.ProviderNotSupportedException;
import com.runiverse.running_service.domain.user.exception.ProviderRequiredException;
import com.runiverse.running_service.domain.user.vo.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OauthLoginHandler implements OauthLoginUsecase {

    private final LoadKakaoProfilePort loadKakaoProfilePort;
    private final LoadGoogleProfilePort loadGoogleProfilePort;
    private final OauthUserResolver oauthUserResolver;
    private final GenerateTokenPort generateTokenPort;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final SaveRefreshTokenHashPort saveRefreshTokenHashPort;
    private final RecordAuthMetricPort recordAuthMetricPort;

    @Override
    public OauthLoginResult handle(OauthLoginCommand command) {
        // 지원하지 않는 provider로 실패하면 null로 남는다
        Provider provider = null;
        try {
            // 1. provider 검증
            provider = resolveProvider(command.provider());
            // 2. provider 자격 증명으로 프로필 확인
            OauthProfile oauthProfile = switch (provider) {
                case KAKAO -> loadKakaoProfilePort.load(command.authorizationCode(), command.codeVerifier());
                case GOOGLE -> loadGoogleProfilePort.load(command.idToken());
            };
            // 3. 조회 or 가입 (트랜잭션)
            User user = oauthUserResolver.findOrRegister(oauthProfile);
            // 4. jwt 토큰 생성
            String accessToken = generateTokenPort.generateAccessToken(user.getUserId());
            String refreshToken = generateTokenPort.generateRefreshToken(user.getUserId());
            // 5. refresh token 해시 후 저장
            saveRefreshTokenHashPort.save(user.getUserId(), refreshTokenHashPort.hash(refreshToken));
            log.info("[인증] 소셜 로그인 성공 - userId={}, provider={}", user.getUserId().value(), provider);
            recordAuthMetricPort.oauthLoginSucceeded(provider);
            // 6. 반환
            return new OauthLoginResult(user.getUserId().value(), accessToken, refreshToken);
        } catch (BusinessException e) {
            // infra가 던지는 코드 교환·토큰 검증 실패·이메일 미동의와 Resolver의 이메일 중복까지 여기서 모두 잡힌다
            recordAuthMetricPort.oauthLoginFailed(provider, e.getErrorCode());
            throw e;
        }
    }

    // 도메인 예외는 500으로 가려지므로 400 UNSUPPORTED_PROVIDER로 바꾼다
    private Provider resolveProvider(String value) {
        try {
            return Provider.from(value);
        } catch (ProviderRequiredException | ProviderNotSupportedException e) {
            log.info("[인증] 소셜 로그인 실패: 지원하지 않는 provider - provider={}", value);
            throw new UnsupportedProviderException();
        }
    }
}
