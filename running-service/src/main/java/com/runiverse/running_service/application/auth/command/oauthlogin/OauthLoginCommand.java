package com.runiverse.running_service.application.auth.command.oauthlogin;

// 그 provider가 쓰는 자격 증명만 채우고 나머지는 null이다 — 구글은 idToken, 카카오는 authorizationCode·codeVerifier
public record OauthLoginCommand(
        String provider,
        String idToken,
        String authorizationCode,
        String codeVerifier
) {

}
