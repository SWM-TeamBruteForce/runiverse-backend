package com.runiverse.running_service.presentation.common.filter;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

// 필터 빈은 톰캣이 뜰 때 바로 만들어져 JPA에 기댈 수 없다 — 대리 필터만 먼저 올리고 진짜 필터는 첫 요청 때 꺼낸다
@Configuration
public class AppVersionFilterConfig {

    @Bean
    public DelegatingFilterProxyRegistrationBean appVersionFilterRegistration() {
        // AppVersionFilter의 빈 이름 — 어긋나면 필터가 자동 등록돼 서버가 뜨지 않으므로 바로 드러난다
        DelegatingFilterProxyRegistrationBean registration =
                new DelegatingFilterProxyRegistrationBean("appVersionFilter");
        // 요청 ID·메트릭 필터 다음, 보안 필터(-100) 앞 — 토큰보다 먼저 보고 거절도 추적·집계된다
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        // 처음 들어온 요청만 검사한다 — SSE 후속 처리·에러 처리는 다시 보지 않는다
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
}
