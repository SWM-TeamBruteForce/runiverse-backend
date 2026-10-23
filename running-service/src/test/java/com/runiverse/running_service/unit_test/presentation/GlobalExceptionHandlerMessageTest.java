package com.runiverse.running_service.unit_test.presentation;

import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;
import com.runiverse.running_service.application.common.exception.UserErrorCode;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 유스케이스 예외의 문구는 ErrorCode가 아니라 예외에서 꺼낸다 — 운영값으로 문구를 만드는 예외가 있다
@DisplayName("예외 핸들러의 유스케이스 예외 문구 단위 테스트")
class GlobalExceptionHandlerMessageTest {

    private static final String CUSTOM_MESSAGE = "운영값으로 만든 문구입니다.";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("문구를 넘긴 예외는 코드는 그대로, 문구는 넘긴 값으로 응답한다")
    void respondsWithCustomMessage() throws Exception {
        mockMvc.perform(post("/probe/custom"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value(CUSTOM_MESSAGE));
    }

    @Test
    @DisplayName("문구를 넘기지 않은 예외는 ErrorCode의 문구로 응답한다")
    void respondsWithErrorCodeMessageByDefault() throws Exception {
        // when & then -> 기존 예외의 응답은 바뀌지 않는다
        mockMvc.perform(post("/probe/default"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.EMAIL_ALREADY_EXISTS.getMessage()));
    }

    @Test
    @DisplayName("비노출 코드는 문구를 넘겨도 500으로 가리고 그 문구를 내보내지 않는다")
    void masksCustomMessageOfHiddenCode() throws Exception {
        mockMvc.perform(post("/probe/masked"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(not(containsString(CUSTOM_MESSAGE))));
    }

    @Test
    @DisplayName("매칭 선택지 위반은 요청 검증 실패와 같은 400 INVALID_REQUEST로 응답한다")
    void respondsInvalidMatchOptionAsInvalidRequest() throws Exception {
        // when & then -> 검증 위치가 DTO에서 유스케이스로 옮겨 가도 클라가 받는 모양은 같다
        mockMvc.perform(post("/probe/match-option"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(CUSTOM_MESSAGE));
    }

    // 문구 생성자는 protected라 테스트 전용 하위 클래스로 연다
    static class CustomMessageException extends BusinessException {

        CustomMessageException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/custom")
        void custom() {
            throw new CustomMessageException(AuthErrorCode.EMAIL_ALREADY_EXISTS, CUSTOM_MESSAGE);
        }

        @PostMapping("/probe/default")
        void defaultMessage() {
            throw new EmailAlreadyExistsException();
        }

        @PostMapping("/probe/masked")
        void masked() {
            throw new CustomMessageException(UserErrorCode.USER_NOT_FOUND, CUSTOM_MESSAGE);
        }

        @PostMapping("/probe/match-option")
        void matchOption() {
            throw new CustomMessageException(MatchErrorCode.INVALID_MATCH_OPTION, CUSTOM_MESSAGE);
        }
    }
}
