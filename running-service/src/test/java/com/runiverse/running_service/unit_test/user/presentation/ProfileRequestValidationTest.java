package com.runiverse.running_service.unit_test.user.presentation;

import com.runiverse.running_service.presentation.user.request.ProfileUpdateRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 온보딩(1-9)과 같은 값을 받으므로 제약과 문구를 같게 유지한다.
// 도메인 예외는 500으로 마스킹되므로 400은 여기서만 만들어진다
@DisplayName("프로필 수정 요청 DTO 검증 단위 테스트")
public class ProfileRequestValidationTest {

    private static final int INTRODUCTION_MAX = 100;

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

    private Set<ConstraintViolation<ProfileUpdateRequest>> validate(ProfileUpdateRequest request) {
        return validator.validate(request);
    }

    private static ProfileUpdateRequest introductionOf(String introduction) {
        return new ProfileUpdateRequest(introduction, null, null, null, null);
    }

    private String firstMessage(Set<ConstraintViolation<ProfileUpdateRequest>> violations) {
        return violations.iterator().next().getMessage();
    }

    @Test
    @DisplayName("모든 필드를 생략해도 통과시킨다")
    void allowsEmptyRequest() {
        // when & then -> 부분 수정이라 보낸 필드만 바꾼다
        assertThat(validate(new ProfileUpdateRequest(null, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("빈 소개글은 지우는 요청이라 통과시킨다")
    void allowsEmptyIntroduction() {
        // when & then -> @NotBlank를 붙이면 소개글을 지울 방법이 사라진다
        assertThat(validate(introductionOf(""))).isEmpty();
    }

    @Test
    @DisplayName("소개글 100자까지 통과시키고 넘으면 명세 문구로 거부한다")
    void checksIntroductionLength() {
        // when & then
        assertThat(validate(introductionOf("가".repeat(INTRODUCTION_MAX)))).isEmpty();
        assertThat(firstMessage(validate(introductionOf("가".repeat(INTRODUCTION_MAX + 1)))))
                .isEqualTo("소개글은 100자 이하여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MALE", "FEMALE", "male", "Female"})
    @DisplayName("성별은 대소문자를 가리지 않는다")
    void allowsGenderIgnoringCase(String gender) {
        // when & then -> Gender VO가 toUpperCase로 정규화하므로 DTO도 맞춘다
        assertThat(validate(new ProfileUpdateRequest(null, gender, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("정해진 값이 아닌 성별은 명세 문구로 거부한다")
    void rejectsUnknownGender() {
        // when & then
        assertThat(firstMessage(validate(new ProfileUpdateRequest(null, "OTHER", null, null, null))))
                .isEqualTo("성별은 MALE 또는 FEMALE이어야 합니다.");
    }

    @Test
    @DisplayName("미래 생년월일은 거부한다")
    void rejectsFutureBirthday() {
        // when & then -> 나이 검증까지 겹쳐 걸리면 메시지 순서가 흔들려 테스트가 깨진다
        ProfileUpdateRequest request =
                new ProfileUpdateRequest(null, null, LocalDate.now().plusDays(1), null, null);
        Set<ConstraintViolation<ProfileUpdateRequest>> violations = validate(request);
        assertThat(violations).hasSize(1);
        assertThat(firstMessage(violations)).isEqualTo("생년월일은 미래일 수 없습니다.");
    }

    @Test
    @DisplayName("1900년 이전 생년월일은 거부한다")
    void rejectsTooOldBirthday() {
        // when & then -> Birthday VO의 하한을 DTO가 함께 들고 있어야 400이 된다
        ProfileUpdateRequest request =
                new ProfileUpdateRequest(null, null, LocalDate.of(1899, 12, 31), null, null);
        assertThat(firstMessage(validate(request)))
                .isEqualTo("생년월일은 1900년 1월 1일 이후여야 합니다.");
    }

    @Test
    @DisplayName("만 14세가 되는 날은 통과시키고 하루라도 모자라면 거부한다")
    void checksMinimumAge() {
        // given -> 고정 날짜로 적으면 시간이 지나며 통과 여부가 바뀐다
        LocalDate turnsFourteenToday = LocalDate.now().minusYears(14);

        // when & then
        assertThat(validate(new ProfileUpdateRequest(null, null, turnsFourteenToday, null, null)))
                .isEmpty();
        assertThat(firstMessage(validate(
                new ProfileUpdateRequest(null, null, turnsFourteenToday.plusDays(1), null, null))))
                .isEqualTo("만 14세 미만은 서비스를 이용할 수 없습니다.");
    }

    @Test
    @DisplayName("몸무게 범위를 벗어나면 명세 문구로 거부한다")
    void checksWeightRange() {
        // when & then
        assertThat(validate(new ProfileUpdateRequest(null, null, null, new BigDecimal("20.0"), null)))
                .isEmpty();
        assertThat(firstMessage(validate(
                new ProfileUpdateRequest(null, null, null, new BigDecimal("19.9"), null))))
                .isEqualTo("몸무게는 20kg 이상이어야 합니다.");
        assertThat(firstMessage(validate(
                new ProfileUpdateRequest(null, null, null, new BigDecimal("300.1"), null))))
                .isEqualTo("몸무게는 300kg 이하여야 합니다.");
    }

    @Test
    @DisplayName("키 범위를 벗어나면 명세 문구로 거부한다")
    void checksHeightRange() {
        // when & then
        assertThat(validate(new ProfileUpdateRequest(null, null, null, null, new BigDecimal("300.0"))))
                .isEmpty();
        assertThat(firstMessage(validate(
                new ProfileUpdateRequest(null, null, null, null, new BigDecimal("19.9")))))
                .isEqualTo("키는 20cm 이상이어야 합니다.");
        assertThat(firstMessage(validate(
                new ProfileUpdateRequest(null, null, null, null, new BigDecimal("300.1")))))
                .isEqualTo("키는 300cm 이하여야 합니다.");
    }
}
