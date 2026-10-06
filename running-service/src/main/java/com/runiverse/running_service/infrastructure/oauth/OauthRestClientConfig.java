package com.runiverse.running_service.infrastructure.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OauthRestClientConfig {

    // provider별 차이는 요청 구성과 응답 해석에 있고 HTTP 설정은 같아 하나를 공유한다
    @Bean
    public RestClient oauthRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
