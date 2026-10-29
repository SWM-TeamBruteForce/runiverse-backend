package com.runiverse.running_service.application.auth.command.logout;

import com.runiverse.running_service.application.auth.port.in.LogoutUsecase;
import com.runiverse.running_service.application.auth.port.out.BlockAccessTokenPort;
import com.runiverse.running_service.application.auth.port.out.DeleteRefreshTokenPort;
import com.runiverse.running_service.application.device.port.out.DeactivateUserDevicesPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LogoutHandler implements LogoutUsecase {

    private final DeactivateUserDevicesPort deactivateUserDevicesPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final BlockAccessTokenPort blockAccessTokenPort;

    @Override
    public void handle(LogoutCommand command) {
        UserId userId = new UserId(command.userId());
        // 1. 이 유저의 기기를 전부 꺼 푸시를 멈춘다 — 뒤의 토큰 폐기가 실패하면 함께 되돌려진다
        deactivateUserDevicesPort.deactivateAll(userId);

        // 2. 저장된 refresh token을 지운다 — 재발급으로 되살아나지 못하게
        deleteRefreshTokenPort.delete(userId);

        // 3. black list에 access token 등록
        blockAccessTokenPort.block(command.accessTokenId());
    }
}
