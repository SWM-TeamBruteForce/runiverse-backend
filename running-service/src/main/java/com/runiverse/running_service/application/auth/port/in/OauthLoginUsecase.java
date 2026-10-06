package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginResult;

public interface OauthLoginUsecase {

    OauthLoginResult handle(OauthLoginCommand command);
}
