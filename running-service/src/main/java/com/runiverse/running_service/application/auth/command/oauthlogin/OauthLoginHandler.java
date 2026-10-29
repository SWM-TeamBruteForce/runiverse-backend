package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.exception.UnsupportedProviderException;
import com.runiverse.running_service.application.auth.port.in.OauthLoginUsecase;
import com.runiverse.running_service.application.auth.port.out.LoadAppleProfilePort;
import com.runiverse.running_service.application.auth.port.out.LoadGoogleProfilePort;
import com.runiverse.running_service.application.auth.port.out.LoadKakaoProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.auth.port.out.RecordAuthMetricPort;
import com.runiverse.running_service.application.common.exception.BusinessException;
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

    private final LoadGoogleProfilePort loadGoogleProfilePort;
    private final LoadKakaoProfilePort loadKakaoProfilePort;
    private final LoadAppleProfilePort loadAppleProfilePort;
    private final OauthSessionIssuer oauthSessionIssuer;
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
                case GOOGLE -> loadGoogleProfilePort.load(command.idToken());
                case KAKAO -> loadKakaoProfilePort.load(command.authorizationCode(), command.codeVerifier());
                case APPLE -> loadAppleProfilePort.load(command.idToken(), command.nonce());
            };
            // 3. 조회 or 가입, 기기 끄기, 토큰 발급 (트랜잭션)
            OauthLoginResult result = oauthSessionIssuer.issue(oauthProfile);
            log.info("[인증] 소셜 로그인 성공 - userId={}, provider={}", result.userId(), provider);
            recordAuthMetricPort.oauthLoginSucceeded(provider);
            // 4. 반환
            return result;
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
