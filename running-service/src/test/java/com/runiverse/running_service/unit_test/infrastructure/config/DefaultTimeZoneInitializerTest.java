package com.runiverse.running_service.unit_test.infrastructure.config;

import com.runiverse.running_service.infrastructure.config.DefaultTimeZoneInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JVM 기본 시간대 초기화 단위 테스트")
class DefaultTimeZoneInitializerTest {

    // JVM 전역 값이라 다른 테스트에 새지 않게 되돌린다
    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("환경이 준비되는 시점에 앱 시간대를 JVM 기본값으로 건다")
    void setsDefaultTimeZoneFromEnvironment() {
        // given -> 실행 환경과 다른 값이어야 바뀐 것을 확인할 수 있다
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // when
        new DefaultTimeZoneInitializer().onApplicationEvent(eventWith("Asia/Seoul"));

        // then
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("없는 지역 이름이면 기동을 멈춘다")
    void rejectsUnknownZone() {
        // when & then -> TimeZone.getTimeZone은 오타를 GMT로 삼키므로 바인딩에서 막는다
        assertThatThrownBy(() -> new DefaultTimeZoneInitializer().onApplicationEvent(eventWith("Asia/Seul")))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("앱 시간대 설정이 없으면 기동을 멈춘다")
    void rejectsMissingZone() {
        // when & then -> 실행 환경의 기본값으로 조용히 넘어가면 저장 시각의 기준이 갈린다
        assertThatThrownBy(() -> new DefaultTimeZoneInitializer().onApplicationEvent(eventWith(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ApplicationEnvironmentPreparedEvent eventWith(String timeZone) {
        return eventWith(Map.of("app.time-zone", timeZone));
    }

    private static ApplicationEnvironmentPreparedEvent eventWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment);
    }
}
