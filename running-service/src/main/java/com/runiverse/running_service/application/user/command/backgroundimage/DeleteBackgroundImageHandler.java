package com.runiverse.running_service.application.user.command.backgroundimage;

import com.runiverse.running_service.application.user.port.in.DeleteBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.out.ClearBackgroundImagePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteBackgroundImageHandler implements DeleteBackgroundImageUsecase {

    private final ClearBackgroundImagePort clearBackgroundImagePort;

    @Override
    public void handle(DeleteBackgroundImageCommand command) {
        // s3 객체를 지우지 않고 DB의 key연결을 끊는다.
        clearBackgroundImagePort.clearBackgroundImage(new UserId(command.userId()));
    }
}
