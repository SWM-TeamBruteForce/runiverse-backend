package com.runiverse.running_service.unit_test.observability;

import com.runiverse.running_service.observability.logging.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("요청 ID 필터 단위 테스트")
public class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청 처리 중에는 UUIDv4 요청 ID가 MDC에 들어 있다")
    void requestIdIsUuidV4DuringRequest() throws Exception {
        // given
        List<String> captured = new ArrayList<>();
        FilterChain chain = (request, response) -> captured.add(MDC.get(RequestIdFilter.REQUEST_ID));

        // when
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then -> 요청 안의 모든 로그가 이 값으로 묶인다
        assertThat(captured).hasSize(1);
        assertThat(UUID.fromString(captured.getFirst()).version()).isEqualTo(4);
    }

    @Test
    @DisplayName("요청마다 다른 요청 ID를 발급한다")
    void requestIdDiffersPerRequest() throws Exception {
        // given
        List<String> captured = new ArrayList<>();
        FilterChain chain = (request, response) -> captured.add(MDC.get(RequestIdFilter.REQUEST_ID));

        // when
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then -> 같으면 서로 다른 요청의 로그가 한 흐름으로 섞인다
        assertThat(captured).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("요청이 끝나면 MDC에서 요청 ID를 지운다")
    void requestIdIsRemovedAfterRequest() throws Exception {
        // given
        FilterChain chain = (request, response) -> { };

        // when
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then -> 톰캣 스레드가 재사용되므로 남으면 다음 요청 로그에 이전 ID가 찍힌다
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("처리 중 예외가 나도 MDC에서 요청 ID를 지운다")
    void requestIdIsRemovedWhenChainThrows() {
        // given
        FilterChain chain = (request, response) -> {
            throw new ServletException("처리 실패");
        };

        // when & then
        assertThatThrownBy(() ->
                filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID)).isNull();
    }
}
