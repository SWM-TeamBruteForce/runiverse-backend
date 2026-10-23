package com.runiverse.running_service.unit_test.app.presentation;

import com.runiverse.running_service.presentation.app.request.AppVersionRequest;
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
@DisplayName("앱 버전 확인 요청 DTO 검증 단위 테스트")
public class AppVersionRequestValidationTest {

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

    private Set<ConstraintViolation<AppVersionRequest>> validate(String platform) {
        return validator.validate(new AppVersionRequest(platform));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"IOS", "ANDROID", "ios", "Android"})
    @DisplayName("플랫폼은 대소문자를 가리지 않는다")
    void allowsPlatformIgnoringCase(String platform) {
        // when & then -> Platform VO가 toUpperCase로 정규화하므로 DTO도 맞춘다
        assertThat(validate(platform)).isEmpty();
    }

    @Test
    @DisplayName("플랫폼이 없으면 필수 메시지만 나온다")
    void rejectsMissingPlatform() {
        // when & then -> 형식 제약이 null에 반응하면 필수 문구에 다른 문구가 겹친다
        assertThat(validate(null))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("플랫폼은 필수입니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("빈 플랫폼은 필수 메시지로 거부한다")
    void rejectsBlankPlatform(String platform) {
        // when & then
        assertThat(validate(platform))
                .extracting(ConstraintViolation::getMessage)
                .contains("플랫폼은 필수입니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"WEB", "AOS", " IOS"})
    @DisplayName("정해진 값이 아닌 플랫폼은 명세 문구로 거부한다")
    void rejectsUnknownPlatform(String platform) {
        // when & then
        assertThat(validate(platform))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("플랫폼은 IOS 또는 ANDROID이어야 합니다.");
    }
}
