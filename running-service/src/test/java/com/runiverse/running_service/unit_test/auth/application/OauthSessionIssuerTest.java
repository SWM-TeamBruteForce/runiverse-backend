package com.runiverse.running_service.unit_test.auth.application;

import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginHandler;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthSessionIssuer;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("소셜 로그인 세션 발급기 단위 테스트")
class OauthSessionIssuerTest {

    @Test
    @DisplayName("조회·가입, 기기 끄기, 토큰 저장을 한 쓰기 트랜잭션으로 연다")
    void opensOneWriteTransaction() throws NoSuchMethodException {
        // when -> 기기 끄기는 벌크 UPDATE라 트랜잭션 밖에서는 500이 된다. 인메모리 통합 테스트는 이를 못 잡는다
        Transactional transactional = OauthSessionIssuer.class
                .getMethod("issue", OauthProfile.class)
                .getAnnotation(Transactional.class);

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("핸들러는 트랜잭션을 열지 않는다")
    void handlerOpensNoTransaction() throws NoSuchMethodException {
        // when & then -> 외부 프로필 조회가 DB 커넥션을 물고 기다리지 않는다
        assertThat(OauthLoginHandler.class.getAnnotation(Transactional.class)).isNull();
        assertThat(OauthLoginHandler.class
                .getMethod("handle", OauthLoginCommand.class)
                .getAnnotation(Transactional.class)).isNull();
    }
}
