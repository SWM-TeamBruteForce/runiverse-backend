package com.runiverse.running_service.unit_test.user.presentation;

import com.runiverse.running_service.presentation.user.request.SettingsUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 도메인 예외는 500으로 마스킹되므로 400은 여기서만 만들어진다
@DisplayName("설정 변경 요청 DTO 검증 단위 테스트")
public class SettingsRequestValidationTest {

    private static final String UNSUPPORTED_VISIBILITY_MESSAGE =
            "프로필 공개 범위는 FRIENDS 또는 PUBLIC이어야 합니다.";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private Set<ConstraintViolation<SettingsUpdateRequest>> validate(
            SettingsUpdateRequest request) {
        return validator.validate(request);
    }

    private static SettingsUpdateRequest visibilityOf(String profileVisibility) {
        return new SettingsUpdateRequest(null, profileVisibility);
    }

    @Test
    @DisplayName("모든 필드를 생략해도 통과시킨다")
    void allowsEmptyRequest() {
        // when & then -> 부분 수정이라 보낸 필드만 바꾼다
        assertThat(validate(new SettingsUpdateRequest(null, null))).isEmpty();
    }

    @Test
    @DisplayName("알림만 담아도 통과시킨다")
    void allowsAlertConsentOnly() {
        // when & then -> 토글 하나만 눌러 보내는 경우다
        assertThat(validate(new SettingsUpdateRequest(false, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FRIENDS", "PUBLIC", "friends", "public", "Public"})
    @DisplayName("허용 값은 대소문자와 무관하게 통과시킨다")
    void allowsSupportedVisibility(String profileVisibility) {
        // when & then -> 성별과 같은 기준으로 받는다
        assertThat(validate(visibilityOf(profileVisibility))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE", "FOLLOWERS", "ALL", "", " "})
    @DisplayName("허용하지 않는 공개 범위는 명세 문구로 거부한다")
    void rejectsUnsupportedVisibility(String profileVisibility) {
        // when & then
        assertThat(validate(visibilityOf(profileVisibility)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(UNSUPPORTED_VISIBILITY_MESSAGE);
    }
}
