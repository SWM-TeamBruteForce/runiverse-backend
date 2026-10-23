package com.runiverse.running_service.unit_test.app.application;

import com.runiverse.running_service.application.app.port.out.LoadAppVersionPort;
import com.runiverse.running_service.application.app.query.version.CheckAppVersionHandler;
import com.runiverse.running_service.application.app.query.version.CheckAppVersionQuery;
import com.runiverse.running_service.domain.app.AppVersion;
import com.runiverse.running_service.domain.common.exception.UnsupportedPlatformException;
import com.runiverse.running_service.domain.common.vo.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("앱 버전 판정 단위 테스트")
class CheckAppVersionHandlerTest {

    @Mock
    private LoadAppVersionPort loadAppVersionPort;

    @InjectMocks
    private CheckAppVersionHandler handler;

    @Test
    @DisplayName("최소 버전보다 낮으면 업데이트가 필요하다")
    void lowerThanMinimumRequiresUpdate() {
        // given
        givenMinimum(Platform.ANDROID, "1.1.0");

        // when & then
        assertThat(handler.handle(new CheckAppVersionQuery("ANDROID", "1.0.9")).updateRequired()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.0", "1.1.1", "2.0.0"})
    @DisplayName("최소 버전 이상이면 업데이트가 필요 없다")
    void atOrAboveMinimumPasses(String versionName) {
        // given
        givenMinimum(Platform.ANDROID, "1.1.0");

        // when & then
        assertThat(handler.handle(new CheckAppVersionQuery("ANDROID", versionName)).updateRequired()).isFalse();
    }

    @Test
    @DisplayName("헤더의 플랫폼은 대소문자를 가리지 않고 그 플랫폼 값으로 판정한다")
    void judgesWithPlatformOfHeader() {
        // given -> 다른 플랫폼 값으로 판정하면 한쪽에만 나온 버전을 다른 쪽에 요구하게 된다
        givenMinimum(Platform.IOS, "1.2.0");

        // when & then
        assertThat(handler.handle(new CheckAppVersionQuery("ios", "1.1.0")).updateRequired()).isTrue();
    }

    @Test
    @DisplayName("플랫폼 행이 없으면 통과시키지 않고 예외를 던진다")
    void missingRowFails() {
        // given -> 통과시키면 막아야 할 요청이 들어온다
        given(loadAppVersionPort.load(Platform.IOS)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new CheckAppVersionQuery("IOS", "1.0.0")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("정해진 플랫폼이 아니면 조회하기 전에 VO가 막는다")
    void unsupportedPlatformIsRejectedBeforeLookup() {
        // when & then -> 필터가 형식을 먼저 거르지만 VO가 마지막 방어선이다
        assertThatThrownBy(() -> handler.handle(new CheckAppVersionQuery("WEB", "1.0.0")))
                .isInstanceOf(UnsupportedPlatformException.class);
        verifyNoInteractions(loadAppVersionPort);
    }

    private void givenMinimum(Platform platform, String minimumVersion) {
        given(loadAppVersionPort.load(platform)).willReturn(Optional.of(AppVersion.builder()
                .platform(platform)
                .minimumVersion(minimumVersion)
                .latestVersion("9.0.0")
                .build()));
    }
}
