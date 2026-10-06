package com.runiverse.running_service.application.auth.command.login;

public record LoginCommand(
        String email,
        String password
) {

}
