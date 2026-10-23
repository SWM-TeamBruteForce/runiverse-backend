package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.port.out.LoadGoogleProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;

import java.util.HashMap;
import java.util.Map;

public class FakeGoogleOauthClient implements LoadGoogleProfilePort {

    private final Map<String, OauthProfile> profiles = new HashMap<>();

    // 테스트 준비 - 구글 ID 토큰에 대응하는 프로필을 미리 심는다
    public void register(String idToken, OauthProfile profile) {
        profiles.put(idToken, profile);
    }

    // 모르는 토큰이면 실제 클라이언트와 같이 검증 실패로 본다
    @Override
    public OauthProfile load(String idToken) {
        OauthProfile profile = profiles.get(idToken);
        if (profile == null) {
            throw new OauthLoginFailedException();
        }
        return profile;
    }
}
