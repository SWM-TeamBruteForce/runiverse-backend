package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.port.out.LoadKakaoProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;

import java.util.HashMap;
import java.util.Map;

public class FakeKakaoOauthClient implements LoadKakaoProfilePort {

    private final Map<String, OauthProfile> profiles = new HashMap<>();

    // 테스트 준비 - 카카오 인가 코드에 대응하는 프로필을 미리 심는다
    public void register(String authorizationCode, OauthProfile profile) {
        profiles.put(authorizationCode, profile);
    }

    // 모르는 인가 코드면 실제 클라이언트와 같이 교환 실패로 본다
    @Override
    public OauthProfile load(String authorizationCode, String codeVerifier) {
        OauthProfile profile = profiles.get(authorizationCode);
        if (profile == null) {
            throw new OauthLoginFailedException();
        }
        return profile;
    }
}
