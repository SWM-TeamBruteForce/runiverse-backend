package com.runiverse.running_service.application.auth.command.logout;

import com.runiverse.running_service.application.auth.port.in.LogoutUsecase;
import com.runiverse.running_service.application.auth.port.out.BlockAccessTokenPort;
import com.runiverse.running_service.application.auth.port.out.DeleteRefreshTokenPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutHandler implements LogoutUsecase {

    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final BlockAccessTokenPort blockAccessTokenPort;

    @Override
    public void handle(LogoutCommand command) {
        // 1. 저장된 refresh token을 지운다 — 재발급으로 되살아나지 못하게
        deleteRefreshTokenPort.delete(new UserId(command.userId()));

        // 2. black list에 access token 등록
        blockAccessTokenPort.block(command.accessTokenId());
    }
}
