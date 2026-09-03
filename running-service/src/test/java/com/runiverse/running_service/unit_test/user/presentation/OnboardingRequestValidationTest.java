package com.runiverse.running_service.unit_test.user.presentation;

import com.runiverse.running_service.presentation.user.request.OnboardingRequest;
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

// 여섯 필드가 전부 필수이고, 프로필 수정과 같은 값을 받으므로 제약과 문구를 같게 유지한다.
// 도메인 예외는 500으로 마스킹되므로 400은 여기서만 만들어진다
@DisplayName("온보딩 요청 DTO 검증 단위 테스트")
public class OnboardingRequestValidationTest {

    private static final String VALID_NICKNAME = "완두콩";
    private static final String VALID_GENDER = "MALE";
    private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1998, 12, 16);
    private static final int VALID_PACE = 359;
    private static final BigDecimal VALID_WEIGHT = new BigDecimal("77.0");
    private static final BigDecimal VALID_HEIGHT = new BigDecimal("175.0");

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

    private Set<ConstraintViolation<OnboardingRequest>> validate(OnboardingRequest request) {
        return validator.validate(request);
    }

    // 한 필드만 바꾼 요청 — 나머지는 유효값이라 위반은 바꾼 필드에서만 나온다
    private static OnboardingRequest nicknameOf(String nickname) {
        return new OnboardingRequest(nickname, VALID_GENDER, VALID_BIRTHDAY, VALID_PACE, VALID_WEIGHT, VALID_HEIGHT);
    }

    private static OnboardingRequest genderOf(String gender) {
        return new OnboardingRequest(VALID_NICKNAME, gender, VALID_BIRTHDAY, VALID_PACE, VALID_WEIGHT, VALID_HEIGHT);
    }

    private static OnboardingRequest birthdayOf(LocalDate birthday) {
        return new OnboardingRequest(VALID_NICKNAME, VALID_GENDER, birthday, VALID_PACE, VALID_WEIGHT, VALID_HEIGHT);
    }

    private static OnboardingRequest paceOf(Integer averagePaceSecondsPerKm) {
        return new OnboardingRequest(
                VALID_NICKNAME, VALID_GENDER, VALID_BIRTHDAY, averagePaceSecondsPerKm, VALID_WEIGHT, VALID_HEIGHT);
    }

    private static OnboardingRequest weightOf(BigDecimal weightKg) {
        return new OnboardingRequest(VALID_NICKNAME, VALID_GENDER, VALID_BIRTHDAY, VALID_PACE, weightKg, VALID_HEIGHT);
    }

    private static OnboardingRequest heightOf(BigDecimal heightCm) {
        return new OnboardingRequest(VALID_NICKNAME, VALID_GENDER, VALID_BIRTHDAY, VALID_PACE, VALID_WEIGHT, heightCm);
    }

    @Test
    @DisplayName("모든 필드가 유효하면 통과한다")
    void validRequestPasses() {
        // when & then
        assertThat(validate(nicknameOf(VALID_NICKNAME))).isEmpty();
    }

    @Test
    @DisplayName("필드를 전부 비우면 필수 메시지 여섯 개만 나온다")
    void rejectsAllMissingFields() {
        // when & then -> 형식·범위 제약이 null에 반응하면 필수 문구에 다른 문구가 겹친다
        assertThat(validate(new OnboardingRequest(null, null, null, null, null, null)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "닉네임은 필수입니다.",
                        "성별은 필수입니다.",
                        "생년월일은 필수입니다.",
                        "평균 페이스는 필수입니다.",
                        "몸무게는 필수입니다.",
                        "키는 필수입니다.");
    }

    // 닉네임 규칙의 세부 경계는 닉네임 요청 테스트가 든다 — 여기서는 온보딩도 같은 규칙인지만 고정한다
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("빈 닉네임은 필수 메시지로 거부한다")
    void rejectsBlankNickname(String nickname) {
        // when & then
        assertThat(validate(nicknameOf(nickname)))
                .extracting(ConstraintViolation::getMessage)
                .contains("닉네임은 필수입니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"가", "가나다라마바사아자차카타파하ABC"})
    @DisplayName("2자 미만·16자 초과 닉네임은 길이 메시지로 거부한다")
    void rejectsOutOfLengthNickname(String nickname) {
        // when & then
        assertThat(validate(nicknameOf(nickname)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("닉네임은 2자 이상 16자 이하여야 합니다.");
    }

    @Test
    @DisplayName("허용하지 않는 문자가 섞인 닉네임은 형식 메시지로 거부한다")
    void rejectsInvalidCharacterNickname() {
        // when & then
        assertThat(validate(nicknameOf("완두콩!")))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"MALE", "FEMALE", "male", "Female"})
    @DisplayName("성별은 대소문자를 가리지 않는다")
    void allowsGenderIgnoringCase(String gender) {
        // when & then -> Gender VO가 toUpperCase로 정규화하므로 DTO도 맞춘다
        assertThat(validate(genderOf(gender))).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("빈 성별은 필수 메시지로 거부한다")
    void rejectsBlankGender(String gender) {
        // when & then
        assertThat(validate(genderOf(gender)))
                .extracting(ConstraintViolation::getMessage)
                .contains("성별은 필수입니다.");
    }

    @Test
    @DisplayName("정해진 값이 아닌 성별은 명세 문구로 거부한다")
    void rejectsUnknownGender() {
        // when & then
        assertThat(validate(genderOf("OTHER")))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("성별은 MALE 또는 FEMALE이어야 합니다.");
    }

    @Test
    @DisplayName("미래 생년월일은 거부한다")
    void rejectsFutureBirthday() {
        // when & then -> 나이 검증까지 겹쳐 걸리면 메시지가 둘 나간다
        assertThat(validate(birthdayOf(LocalDate.now().plusDays(1))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("생년월일은 미래일 수 없습니다.");
    }

    @Test
    @DisplayName("1900년 이전 생년월일은 거부한다")
    void rejectsTooOldBirthday() {
        // when & then -> Birthday VO의 하한을 DTO가 함께 들고 있어야 400이 된다
        assertThat(validate(birthdayOf(LocalDate.of(1899, 12, 31))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("생년월일은 1900년 1월 1일 이후여야 합니다.");
    }

    @Test
    @DisplayName("만 14세가 되는 날은 통과시키고 하루라도 모자라면 거부한다")
    void checksMinimumAge() {
        // given -> 고정 날짜로 적으면 시간이 지나며 통과 여부가 바뀐다
        LocalDate turnsFourteenToday = LocalDate.now().minusYears(14);

        // when & then
        assertThat(validate(birthdayOf(turnsFourteenToday))).isEmpty();
        assertThat(validate(birthdayOf(turnsFourteenToday.plusDays(1))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("만 14세 미만은 서비스를 이용할 수 없습니다.");
    }

    @Test
    @DisplayName("평균 페이스 범위를 벗어나면 명세 문구로 거부한다")
    void checksPaceRange() {
        // when & then
        assertThat(validate(paceOf(120))).isEmpty();
        assertThat(validate(paceOf(1800))).isEmpty();
        assertThat(validate(paceOf(119)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("평균 페이스는 120초 이상이어야 합니다.");
        assertThat(validate(paceOf(1801)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("평균 페이스는 1800초 이하여야 합니다.");
    }

    @Test
    @DisplayName("몸무게 범위를 벗어나면 명세 문구로 거부한다")
    void checksWeightRange() {
        // when & then
        assertThat(validate(weightOf(new BigDecimal("20.0")))).isEmpty();
        assertThat(validate(weightOf(new BigDecimal("300.0")))).isEmpty();
        assertThat(validate(weightOf(new BigDecimal("19.9"))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("몸무게는 20kg 이상이어야 합니다.");
        assertThat(validate(weightOf(new BigDecimal("300.1"))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("몸무게는 300kg 이하여야 합니다.");
    }

    @Test
    @DisplayName("키 범위를 벗어나면 명세 문구로 거부한다")
    void checksHeightRange() {
        // when & then
        assertThat(validate(heightOf(new BigDecimal("20.0")))).isEmpty();
        assertThat(validate(heightOf(new BigDecimal("300.0")))).isEmpty();
        assertThat(validate(heightOf(new BigDecimal("19.9"))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("키는 20cm 이상이어야 합니다.");
        assertThat(validate(heightOf(new BigDecimal("300.1"))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("키는 300cm 이하여야 합니다.");
    }
}
