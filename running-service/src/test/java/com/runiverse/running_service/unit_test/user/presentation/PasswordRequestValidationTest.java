package com.runiverse.running_service.unit_test.user.presentation;

import com.runiverse.running_service.presentation.auth.request.SignUpRequest;
import com.runiverse.running_service.presentation.user.request.PasswordUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 비밀번호 정책(6~16자, 영문·숫자·특수문자 각 1자 이상)은 가입과 변경이 같아야 한다.
// 도메인 예외는 500으로 마스킹되므로 400은 여기서만 만들어진다
@DisplayName("비밀번호 변경 요청 DTO 검증 단위 테스트")
public class PasswordRequestValidationTest {

    private static final String VALID_PASSWORD = "Password123!";

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

    private Set<ConstraintViolation<Object>> validateNewPassword(String newPassword) {
        Set<ConstraintViolation<Object>> violations =
                validator.validate((Object) new PasswordUpdateRequest(VALID_PASSWORD, newPassword));
        Set<ConstraintViolation<Object>> signUp =
                validator.validate((Object) new SignUpRequest("verification-ticket", newPassword));

        // 가입과 변경의 정책이 갈라지면 한쪽에서만 통과하는 비밀번호가 생긴다
        assertThat(signUp).hasSameSizeAs(violations);
        return violations;
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"Pass1!", "Password123!", "aB3!aB3!aB3!aB3!", "runner1@"})
    @DisplayName("6~16자에 영문·숫자·특수문자를 모두 담으면 통과한다")
    void validPasswordPasses(String newPassword) {
        assertThat(validateNewPassword(newPassword)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("비어 있으면 필수 메시지로 막는다")
    void blankPasswordIsRejected(String newPassword) {
        assertThat(validateNewPassword(newPassword))
                .extracting(ConstraintViolation::getMessage)
                .contains("비밀번호는 필수입니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"aB3!a", "aB3!aB3!aB3!aB3!7"})
    @DisplayName("6자 미만·16자 초과는 길이 메시지로 막는다")
    void outOfLengthPasswordIsRejected(String newPassword) {
        assertThat(validateNewPassword(newPassword))
                .extracting(ConstraintViolation::getMessage)
                .contains("비밀번호는 6자 이상 16자 이하여야 합니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"password123", "Password!!!", "12345678!", "PASSWORD123"})
    @DisplayName("영문·숫자·특수문자 중 하나라도 빠지면 조합 메시지로 막는다")
    void missingCharacterClassIsRejected(String newPassword) {
        assertThat(validateNewPassword(newPassword))
                .extracting(ConstraintViolation::getMessage)
                .contains("비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다.");
    }

    @Test
    @DisplayName("현재 비밀번호는 비어 있을 때만 막는다")
    void currentPasswordIsOnlyCheckedForBlank() {
        // 지금 정책 이전에 만들어진 비밀번호도 대조는 되어야 한다.
        // 여기에 형식 제약을 걸면 그런 계정은 비밀번호를 영영 바꿀 수 없다
        assertThat(validator.validate(new PasswordUpdateRequest("old", VALID_PASSWORD))).isEmpty();
        assertThat(validator.validate(new PasswordUpdateRequest(null, VALID_PASSWORD)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("현재 비밀번호는 필수입니다.");
    }
}
