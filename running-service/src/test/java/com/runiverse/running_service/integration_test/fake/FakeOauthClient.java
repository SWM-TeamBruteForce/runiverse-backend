package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.exception.OauthLoginFailedException;
import com.runiverse.running_service.application.auth.port.out.LoadGoogleProfilePort;
import com.runiverse.running_service.application.auth.port.out.LoadKakaoProfilePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;

import java.util.HashMap;
import java.util.Map;

public class FakeOauthClient implements LoadKakaoProfilePort, LoadGoogleProfilePort {

    private final Map<String, OauthProfile> profiles = new HashMap<>();

    // 테스트 준비 - 카카오 인가 코드나 구글 ID 토큰에 대응하는 프로필을 미리 심는다
    public void register(String credential, OauthProfile profile) {
        profiles.put(credential, profile);
    }

    @Override
    public OauthProfile load(String authorizationCode, String codeVerifier) {
        return find(authorizationCode, Provider.KAKAO);
    }

    @Override
    public OauthProfile load(String idToken) {
        return find(idToken, Provider.GOOGLE);
    }

    // 없거나 다른 provider의 자격 증명이면 실제 클라이언트와 같이 실패로 본다
    private OauthProfile find(String credential, Provider provider) {
        OauthProfile profile = profiles.get(credential);
        if (profile == null || profile.provider() != provider) {
            throw new OauthLoginFailedException();
        }
        return profile;
    }
}
