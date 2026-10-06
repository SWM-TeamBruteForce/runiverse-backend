package com.runiverse.running_service.unit_test.user.presentation;

import com.runiverse.running_service.presentation.user.request.NicknameAvailabilityRequest;
import com.runiverse.running_service.presentation.user.request.NicknameUpdateRequest;
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

// 두 DTO가 Nickname VO(2~16자, 한글·영문·숫자·_)와 같은 제약을 들고 있는지 함께 고정한다.
// 도메인 예외는 500으로 마스킹되므로 400은 여기서만 만들어진다
@DisplayName("닉네임 요청 DTO 검증 단위 테스트")
public class NicknameRequestValidationTest {

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

    private Set<ConstraintViolation<Object>> validateBoth(String nickname) {
        Set<ConstraintViolation<Object>> violations =
                validator.validate((Object) new NicknameUpdateRequest(nickname));
        Set<ConstraintViolation<Object>> availability =
                validator.validate((Object) new NicknameAvailabilityRequest(nickname));

        // 두 DTO가 갈라지면 한쪽 화면만 400을 못 받게 된다
        assertThat(availability).hasSameSizeAs(violations);
        return violations;
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"완두콩", "가나", "러너킴12", "Runner_01", "가나다라마바사아자차카타파하ab"})
    @DisplayName("2~16자 한글·영문·숫자·_ 조합은 통과한다")
    void validNicknamePasses(String nickname) {
        assertThat(validateBoth(nickname)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("비어 있으면 필수 메시지로 막는다")
    void blankNicknameIsRejected(String nickname) {
        assertThat(validateBoth(nickname))
                .extracting(ConstraintViolation::getMessage)
                .contains("닉네임은 필수입니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"가", "가나다라마바사아자차카타파하ABC"})
    @DisplayName("2자 미만·16자 초과는 길이 메시지로 막는다")
    void outOfLengthNicknameIsRejected(String nickname) {
        assertThat(validateBoth(nickname))
                .extracting(ConstraintViolation::getMessage)
                .contains("닉네임은 2자 이상 16자 이하여야 합니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"완두콩!", "runner kim", "한글English혼용ok?", "이모지🏃"})
    @DisplayName("허용하지 않는 문자가 섞이면 형식 메시지로 막는다")
    void invalidCharacterIsRejected(String nickname) {
        assertThat(validateBoth(nickname))
                .extracting(ConstraintViolation::getMessage)
                .contains("닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.");
    }

    @Test
    @DisplayName("앞뒤 공백이 붙으면 VO와 달리 다듬지 않고 400으로 막는다")
    void surroundingWhitespaceIsRejected() {
        // Nickname VO는 trim() 후 통과시키지만 Bean Validation은 원본에 걸린다.
        // 온보딩 요청도 같은 규칙이라 일관되며, 공백을 허용하려면 두 DTO를 함께 바꿔야 한다
        assertThat(validateBoth("  완두콩  "))
                .extracting(ConstraintViolation::getMessage)
                .contains("닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.");
    }
}
