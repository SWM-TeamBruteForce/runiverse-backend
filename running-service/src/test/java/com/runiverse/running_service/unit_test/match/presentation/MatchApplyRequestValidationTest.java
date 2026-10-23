package com.runiverse.running_service.unit_test.match.presentation;

import com.runiverse.running_service.presentation.match.request.ApplyMatchRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// DTO는 값의 존재만 본다. 선택지(시각·거리)는 운영값이라 ApplyMatchHandler가 MatchSlotPolicy로 보고,
// 그 판정은 MatchSlotPolicyTest가 다룬다
@DisplayName("매칭 신청 요청 DTO 검증 단위 테스트")
class MatchApplyRequestValidationTest {

    private static final LocalDateTime VALID = LocalDate.of(2026, 9, 11).atTime(19, 0);
    private static final int DISTANCE = 5_000;

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
    @DisplayName("값이 있으면 통과한다")
    void presentValuesPass() {
        // when & then
        assertThat(validator.validate(new ApplyMatchRequest(VALID, DISTANCE))).isEmpty();
    }

    @Test
    @DisplayName("선택지 밖의 값도 DTO에서는 거절하지 않는다")
    void doesNotJudgeOptions() {
        // given -> 범위 밖·간격 밖 시각과 목록 밖 거리
        ApplyMatchRequest request = new ApplyMatchRequest(VALID.withHour(23).withMinute(15), 7_000);

        // when & then -> 여기서 다시 하드코딩해 막으면 설정을 바꿔도 DTO가 옛 선택지로 거절한다
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("시각이 비면 필수 위반 하나만 낸다")
    void nullTimeReportsOnlyRequired() {
        // given & when
        Set<ConstraintViolation<ApplyMatchRequest>> violations =
                validator.validate(new ApplyMatchRequest(null, DISTANCE));

        // then
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("희망 시작 시각은 필수입니다.");
    }

    @Test
    @DisplayName("목표 거리가 비면 필수 위반 하나만 낸다")
    void nullDistanceReportsOnlyRequired() {
        // given & when
        Set<ConstraintViolation<ApplyMatchRequest>> violations =
                validator.validate(new ApplyMatchRequest(VALID, null));

        // then
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("목표 거리는 필수입니다.");
    }
}
