package com.runiverse.running_service.unit_test.observability;

import com.runiverse.running_service.observability.logging.LogTag;
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

@DisplayName("로그 태그 단위 테스트")
public class LogTagTest {

    static Stream<Arguments> controllers() {
        return Stream.of(
                Arguments.of(AuthController.class, "[인증]"),
                Arguments.of(UserController.class, "[회원]"),
                Arguments.of(RunningMatchController.class, "[매칭]"),
                Arguments.of(RunningRoomController.class, "[러닝]")
        );
    }

    @ParameterizedTest
    @MethodSource("controllers")
    @DisplayName("요청을 처리한 컨트롤러의 패키지로 태그를 정한다")
    void tagFollowsControllerPackage(Class<?> controller, String expected) throws Exception {
        // given
        MockHttpServletRequest request = requestHandledBy(controller);

        // when & then -> 패키지 이름이 바뀌면 에러 로그가 [공통]으로 뭉개진다
        assertThat(LogTag.of(request)).isEqualTo(expected);
    }

    @Test
    @DisplayName("컨트롤러에 닿기 전에 난 요청은 [공통]이다")
    void noHandlerIsCommon() {
        // given -> 없는 경로·보안 필터 거절은 처리한 핸들러가 없다
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when & then
        assertThat(LogTag.of(request)).isEqualTo("[공통]");
    }

    @Test
    @DisplayName("컨트롤러 메서드가 아닌 핸들러는 [공통]이다")
    void nonMethodHandlerIsCommon() {
        // given -> 정적 리소스 핸들러처럼 HandlerMethod가 아닌 경우
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new Object());

        // when & then
        assertThat(LogTag.of(request)).isEqualTo("[공통]");
    }

    @Test
    @DisplayName("태그 목록에 없는 presentation 패키지는 [공통]이다")
    void unknownPresentationPackageIsCommon() throws Exception {
        // given
        MockHttpServletRequest request = requestHandledBy(GlobalExceptionHandler.class);

        // when & then
        assertThat(LogTag.of(request)).isEqualTo("[공통]");
    }

    @Test
    @DisplayName("presentation 밖의 핸들러는 [공통]이다")
    void handlerOutsidePresentationIsCommon() throws Exception {
        // given
        MockHttpServletRequest request = requestHandledBy(LogTagTest.class);

        // when & then
        assertThat(LogTag.of(request)).isEqualTo("[공통]");
    }

    private MockHttpServletRequest requestHandledBy(Class<?> handlerType) throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HandlerMethod handlerMethod = new HandlerMethod(mock(handlerType), handlerType.getMethod("toString"));
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handlerMethod);
        return request;
    }
}
