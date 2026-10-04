package com.runiverse.running_service.infrastructure.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationListener;

import java.time.ZoneId;
import java.util.TimeZone;

// 컨텍스트 전에 맞춰야 한다 — Hibernate가 감사 컬럼 시계를 기동 중에 고정해, @PostConstruct에서 바꾸면 그 컬럼만 UTC로 찍힌다.
// main()을 거치지 않는 기동(@SpringBootTest 등)에도 걸리도록 META-INF/spring.factories로 등록한다
public class DefaultTimeZoneInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        // 없는 지역 이름은 ZoneId 변환에서 실패한다 — TimeZone.getTimeZone은 오타를 GMT로 삼킨다
        ZoneId zone = Binder.get(event.getEnvironment())
                .bind("app.time-zone", ZoneId.class)
                .orElseThrow(() -> new IllegalStateException("app.time-zone이 없다"));
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
    }
}
