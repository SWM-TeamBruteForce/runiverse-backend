package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.port.out.LoadAppleProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;

import java.util.HashMap;
import java.util.Map;

public class FakeAppleOauthClient implements LoadAppleProfilePort {

    private final Map<String, OauthProfile> profiles = new HashMap<>();

    // 테스트 준비 - 애플 ID 토큰에 대응하는 프로필을 미리 심는다
    public void register(String idToken, OauthProfile profile) {
        profiles.put(idToken, profile);
    }

    // 모르는 토큰이면 실제 클라이언트와 같이 검증 실패로 본다. nonce 대조는 실제 클라이언트의 일이라 보지 않는다
    @Override
    public OauthProfile load(String idToken, String nonce) {
        OauthProfile profile = profiles.get(idToken);
        if (profile == null) {
            throw new OauthLoginFailedException();
        }
        return profile;
    }
}
