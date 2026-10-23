package com.runiverse.running_service.unit_test.match.presentation;

import com.runiverse.running_service.presentation.match.request.MatchSlotsRequest;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 쿼리 파라미터를 글자로 받아 형식을 직접 본다. 거리 선택지는 MatchSlotPolicyTest가 다룬다
@DisplayName("슬롯 조회 요청 DTO 검증 단위 테스트")
class MatchSlotsRequestValidationTest {

    private static final String TODAY = LocalDate.now().toString();

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

    @Test
    @DisplayName("오늘·미래 날짜와 숫자 거리는 통과한다")
    void validValuesPass() {
        // when & then
        assertThat(validate(TODAY, "5000")).isEmpty();
        assertThat(validate(LocalDate.now().plusDays(30).toString(), "5000")).isEmpty();
    }

    @Test
    @DisplayName("날짜를 생략하면 통과하고 오늘로 채운다")
    void omittedDateMeansToday() {
        // when & then
        assertThat(validate(null, "5000")).isEmpty();
        assertThat(new MatchSlotsRequest(null, "5000").dateOrToday()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("선택지 밖 거리도 형식이 맞으면 DTO는 통과시킨다")
    void doesNotJudgeDistanceOptions() {
        // when & then -> 여기서 다시 하드코딩해 막으면 설정을 바꿔도 DTO가 옛 선택지로 거절한다
        assertThat(validate(TODAY, "7000")).isEmpty();
    }

    @Test
    @DisplayName("오늘 이전 날짜는 거절한다")
    void pastDateFails() {
        // when & then
        assertThat(validate(LocalDate.now().minusDays(1).toString(), "5000"))
                .containsExactly("조회 날짜는 오늘 이전일 수 없습니다.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"2026/09/11", "2026-9-11", "2026-02-30", "20260-09-11", ""})
    @DisplayName("날짜 형식이 틀리면 공통 문구 하나만 낸다")
    void malformedDateFails(String date) {
        // when & then -> 과거 판정까지 걸려 문구가 둘 나가지 않는다
        assertThat(validate(date, "5000")).containsExactly("입력값이 올바르지 않습니다.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"abc", "-5000", "5000.0", "1234567"})
    @DisplayName("거리가 숫자가 아니거나 너무 길면 공통 문구로 거절한다")
    void malformedDistanceFails(String distance) {
        // when & then -> int로 바꿀 수 없는 값이 핸들러까지 가지 않는다
        assertThat(validate(TODAY, distance)).containsExactly("입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("거리가 비면 필수 위반 하나만 낸다")
    void missingDistanceReportsOnlyRequired() {
        // when & then
        assertThat(validate(TODAY, null)).containsExactly("목표 거리는 필수입니다.");
        assertThat(validate(TODAY, " ")).containsExactly("목표 거리는 필수입니다.");
    }

    private List<String> validate(String date, String distance) {
        return validator.validate(new MatchSlotsRequest(date, distance)).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }
}
