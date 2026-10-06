package com.runiverse.running_service.unit_test.observability;

import com.runiverse.running_service.observability.RequestDomain;
import com.runiverse.running_service.presentation.auth.controller.AuthController;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.match.controller.RunningMatchController;
import com.runiverse.running_service.presentation.running.controller.RunningRoomController;
import com.runiverse.running_service.presentation.user.controller.UserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("요청 도메인 판별 단위 테스트")
class RequestDomainTest {

    static Stream<Arguments> controllers() {
        return Stream.of(
                Arguments.of(AuthController.class, "auth"),
                Arguments.of(UserController.class, "user"),
                Arguments.of(RunningMatchController.class, "match"),
                Arguments.of(RunningRoomController.class, "running")
        );
    }

    @ParameterizedTest
    @MethodSource("controllers")
    @DisplayName("요청을 처리한 컨트롤러의 패키지로 도메인을 정한다")
    void domainFollowsControllerPackage(Class<?> controller, String expected) throws Exception {
        // given
        MockHttpServletRequest request = requestHandledBy(controller);

        // when & then -> 메트릭 domain 태그와 로그 태그가 이 한 곳에서 갈린다
        assertThat(RequestDomain.of(request)).isEqualTo(expected);
    }

    @Test
    @DisplayName("컨트롤러에 닿기 전에 끝난 요청은 common이다")
    void noHandlerIsCommon() {
        // given -> 없는 경로·보안 필터 거절은 처리한 핸들러가 없다
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when & then
        assertThat(RequestDomain.of(request)).isEqualTo(RequestDomain.COMMON);
    }

    @Test
    @DisplayName("목록에 없는 presentation 패키지는 common이다")
    void unknownPresentationPackageIsCommon() throws Exception {
        // given -> 목록 밖 값을 그대로 쓰면 태그 값이 예고 없이 늘어난다
        MockHttpServletRequest request = requestHandledBy(GlobalExceptionHandler.class);

        // when & then
        assertThat(RequestDomain.of(request)).isEqualTo(RequestDomain.COMMON);
    }

    @Test
    @DisplayName("presentation 밖의 핸들러는 common이다")
    void handlerOutsidePresentationIsCommon() throws Exception {
        // given
        MockHttpServletRequest request = requestHandledBy(RequestDomainTest.class);

        // when & then
        assertThat(RequestDomain.of(request)).isEqualTo(RequestDomain.COMMON);
    }

    private MockHttpServletRequest requestHandledBy(Class<?> handlerType) throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HandlerMethod handlerMethod = new HandlerMethod(mock(handlerType), handlerType.getMethod("toString"));
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handlerMethod);
        return request;
    }
}
