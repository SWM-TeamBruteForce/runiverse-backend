package com.runiverse.running_service.application.auth.command.refresh;

import com.runiverse.running_service.application.auth.exception.InvalidRefreshTokenException;
import com.runiverse.running_service.application.auth.port.in.RefreshUsecase;
import com.runiverse.running_service.application.auth.port.out.DeleteRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.GenerateTokenPort;
import com.runiverse.running_service.application.auth.port.out.LoadRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.ParseRefreshTokenPort;
import com.runiverse.running_service.application.auth.port.out.RefreshTokenHashPort;
import com.runiverse.running_service.application.auth.port.out.SaveRefreshTokenHashPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshHandler implements RefreshUsecase {

    private final ParseRefreshTokenPort parseRefreshTokenPort;
    private final LoadRefreshTokenPort loadRefreshTokenPort;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final GenerateTokenPort generateTokenPort;
    private final SaveRefreshTokenHashPort saveRefreshTokenHashPort;

    @Override
    public RefreshResult handle(RefreshCommand command) {
        // 1. refresh token 검증 후 소유자 확인 (서명, 만료, issuer, audience)
        UserId userId = parseRefreshTokenPort.parse(command.refreshToken())
                .orElseThrow(() -> {
                    log.info("[인증] 토큰 재발급 실패: 토큰 검증 실패");
                    return new InvalidRefreshTokenException();
                });

        // 2. 저장된 해시 조회
        String storedHash = loadRefreshTokenPort.load(userId)
                .orElseThrow(() -> {
                    log.info("[인증] 토큰 재발급 실패: 저장된 토큰 없음 - userId={}", userId.value());
                    return new InvalidRefreshTokenException();
                });

        // 3. 대조 — 불일치 시 탈취로 보고 폐기
        if (!refreshTokenHashPort.matches(command.refreshToken(), storedHash)) {
            log.warn("[인증] 토큰 재발급 실패: 저장된 토큰과 불일치 - userId={}", userId.value());
            deleteRefreshTokenPort.delete(userId);
            throw new InvalidRefreshTokenException();
        }

        // 4. 새 access token, refresh token 발급 (로테이션)
        String newAccessToken = generateTokenPort.generateAccessToken(userId);
        String newRefreshToken = generateTokenPort.generateRefreshToken(userId);

        // 5. 새 refresh token 지문 저장
        saveRefreshTokenHashPort.save(userId, refreshTokenHashPort.hash(newRefreshToken));
        log.info("[인증] 토큰 재발급 성공 - userId={}", userId.value());

        // 6. 반환
        return new RefreshResult(newAccessToken, newRefreshToken);
    }
}
