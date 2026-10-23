package com.runiverse.running_service.unit_test.app.application;

import com.runiverse.running_service.application.app.port.out.LoadAppVersionPort;
import com.runiverse.running_service.application.app.query.version.GetAppVersionHandler;
import com.runiverse.running_service.application.app.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.app.query.version.GetAppVersionResult;
import com.runiverse.running_service.domain.app.AppVersion;
import com.runiverse.running_service.domain.common.exception.UnsupportedPlatformException;
import com.runiverse.running_service.domain.common.vo.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("앱 버전 확인 단위 테스트")
class GetAppVersionHandlerTest {

    @Mock
    private LoadAppVersionPort loadAppVersionPort;

    @InjectMocks
    private GetAppVersionHandler handler;

    @Test
    @DisplayName("요청한 플랫폼의 최소·최신 버전을 돌려준다")
    void returnsVersionsOfPlatform() {
        // given -> 소문자로 와도 VO가 IOS로 읽어 조회한다
        given(loadAppVersionPort.load(Platform.IOS))
                .willReturn(Optional.of(appVersion(Platform.IOS, "1.1.0", "1.2.0")));

        // when
        GetAppVersionResult result = handler.handle(new GetAppVersionQuery("ios"));

        // then
        assertThat(result).isEqualTo(new GetAppVersionResult("1.1.0", "1.2.0"));
    }

    @Test
    @DisplayName("플랫폼 행이 없으면 운영 실수로 보고 예외를 던진다")
    void missingRowFails() {
        // given -> 배포 SQL이 두 행을 미리 넣는다. 없는 값을 지어내 내리지 않는다
        given(loadAppVersionPort.load(Platform.ANDROID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetAppVersionQuery("ANDROID")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("정해진 플랫폼이 아니면 조회하기 전에 VO가 막는다")
    void unsupportedPlatformIsRejectedBeforeLookup() {
        // when & then -> 요청 DTO가 400으로 거르지만 VO가 마지막 방어선이다
        assertThatThrownBy(() -> handler.handle(new GetAppVersionQuery("WEB")))
                .isInstanceOf(UnsupportedPlatformException.class);
        verifyNoInteractions(loadAppVersionPort);
    }

    private static AppVersion appVersion(Platform platform, String minimumVersion, String latestVersion) {
        return AppVersion.builder()
                .platform(platform)
                .minimumVersion(minimumVersion)
                .latestVersion(latestVersion)
                .build();
    }
}
