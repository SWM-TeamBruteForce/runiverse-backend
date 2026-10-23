package com.runiverse.running_service.unit_test.presentation;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// 응답을 더 쓸 수 없게 된 요청이 "처리하지 못한 예외"로 ERROR를 남기지 않는지 실제 핸들러 선택 경로로 확인한다.
// 매칭 SSE를 앱이 닫으면 다음 쓰기가 실패하며 이 예외가 예외 핸들러까지 온다
@DisplayName("응답 사용 불가 예외 처리 단위 테스트")
class GlobalExceptionHandlerDisconnectTest {

    private MockMvc mockMvc;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        log = LogCapture.of(GlobalExceptionHandler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("응답을 쓸 수 없게 된 요청은 ERROR 없이 INFO 한 줄만 남기고 본문을 쓰지 않는다")
    void responseNotUsable() throws Exception {
        // when
        MockHttpServletResponse response = mockMvc.perform(get("/probe/not-usable")).andReturn().getResponse();

        // then
        assertThat(log.messages(Level.ERROR)).isEmpty();
        assertThat(log.messages(Level.INFO))
                .containsExactly("[공통] 응답 건너뜀: 응답 사용 불가 - method=GET, path=/probe/not-usable");
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("다른 예상 못 한 예외는 그대로 ERROR와 500이다")
    void otherUnexpectedException() throws Exception {
        // when
        MockHttpServletResponse response = mockMvc.perform(get("/probe/unexpected")).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(log.events(Level.ERROR)).singleElement()
                .satisfies(event -> assertThat(event.getThrowableProxy()).isNotNull());
    }

    @RestController
    static class ProbeController {

        // 실제로는 끊긴 연결에 쓰다 실패하면 컨테이너 오류 통지를 거쳐 스프링이 던진다
        @GetMapping("/probe/not-usable")
        void notUsable() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("ServletOutputStream failed to write: Broken pipe");
        }

        @GetMapping("/probe/unexpected")
        void unexpected() {
            throw new IllegalStateException("boom");
        }
    }
}
