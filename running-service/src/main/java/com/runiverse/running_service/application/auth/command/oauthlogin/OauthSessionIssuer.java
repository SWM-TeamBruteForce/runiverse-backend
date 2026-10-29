package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenHashPort;
import com.runiverse.running_service.application.device.port.out.DeactivateUserDevicesPort;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 확인된 소셜 프로필로 로그인 세션을 연다 — 토큰 저장에 실패하면 가입·기기 끄기도 함께 롤백된다
// 리프레시 토큰은 Redis에 저장해 이 트랜잭션에 들지 않는다. 외부 프로필 조회는 Handler가 트랜잭션 밖에서 끝낸다
@Component
@RequiredArgsConstructor
public class OauthSessionIssuer {

    private final OauthUserResolver oauthUserResolver;
    private final DeactivateUserDevicesPort deactivateUserDevicesPort;
    private final GenerateTokenPort generateTokenPort;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final SaveRefreshTokenHashPort saveRefreshTokenHashPort;

    @Transactional
    public OauthLoginResult issue(OauthProfile oauthProfile) {
        // 1. 조회 or 가입
        User user = oauthUserResolver.findOrRegister(oauthProfile);
        // 2. 다른 기기에서 쓰던 세션이 이 로그인으로 끝난다 — 그 기기들로 푸시가 가지 않게 끈다
        deactivateUserDevicesPort.deactivateAll(user.getUserId());
        // 3. jwt 토큰 생성
        String accessToken = generateTokenPort.generateAccessToken(user.getUserId());
        String refreshToken = generateTokenPort.generateRefreshToken(user.getUserId());
        // 4. refresh token 해시 후 저장
        saveRefreshTokenHashPort.save(user.getUserId(), refreshTokenHashPort.hash(refreshToken));
        // 5. 반환
        return new OauthLoginResult(user.getUserId().value(), accessToken, refreshToken);
    }
}
