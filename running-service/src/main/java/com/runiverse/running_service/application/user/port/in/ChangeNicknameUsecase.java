package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameResult;

public interface ChangeNicknameUsecase {

    ChangeNicknameResult handle(ChangeNicknameCommand command);
}
