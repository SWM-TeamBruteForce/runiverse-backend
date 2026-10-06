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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 허용 슬롯과 목표 거리는 클라가 시간·거리 선택지를 그리는 근거다.
// 도메인 예외는 500으로 마스킹되므로 400은 이 DTO에서만 만들어진다
@DisplayName("매칭 신청 요청 DTO 검증 단위 테스트")
class MatchApplyRequestValidationTest {

    private static final LocalTime VALID = LocalTime.of(19, 0);

    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);
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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"18:00", "18:30", "19:00", "21:30", "22:00"})
    @DisplayName("18:00~22:00 30분 간격 슬롯은 통과한다")
    void allowedSlotsPass(String time) {
        // when & then -> 양 끝 경계도 선택지에 들어간다
        assertThat(validate(LocalTime.parse(time), DISTANCE)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"00:00", "17:30", "17:59", "22:01", "23:59"})
    @DisplayName("창 밖 시각은 거절한다")
    void outOfWindowFails(String time) {
        // when & then -> 자유 입력이 아니라 정해진 선택지다
        assertThat(validate(LocalTime.parse(time), DISTANCE)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"18:01", "19:15", "21:45"})
    @DisplayName("창 안이어도 30분 간격이 아니면 거절한다")
    void unalignedMinuteFails(String time) {
        // when & then
        assertThat(validate(LocalTime.parse(time), DISTANCE)).hasSize(1);
    }

    @Test
    @DisplayName("초·나노가 붙으면 거절한다")
    void secondsAndNanosFail() {
        // given -> 분만 보면 통과하지만 슬롯이 아니다
        LocalTime withSeconds = VALID.withSecond(30);
        LocalTime withNanos = VALID.withNano(1);

        // when & then
        assertThat(validate(withSeconds, DISTANCE)).hasSize(1);
        assertThat(validate(withNanos, DISTANCE)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}m")
    @ValueSource(ints = {3_000, 5_000, 10_000})
    @DisplayName("허용된 목표 거리는 통과한다")
    void allowedDistancePasses(int distance) {
        // when & then
        assertThat(validate(VALID, distance)).isEmpty();
    }

    @ParameterizedTest(name = "{0}m")
    @ValueSource(ints = {0, -5_000, 1_000, 4_000, 7_000, 20_000})
    @DisplayName("허용되지 않은 목표 거리는 거절한다")
    void disallowedDistanceFails(int distance) {
        // when & then -> 자유 입력이 아니라 정해진 선택지다
        assertThat(validate(VALID, distance)).hasSize(1);
    }

    @Test
    @DisplayName("시각이 비면 필수 위반 하나만 낸다")
    void nullTimeReportsOnlyRequired() {
        // given & when
        Set<ConstraintViolation<ApplyMatchRequest>> violations =
                validator.validate(new ApplyMatchRequest(null, DISTANCE));

        // then -> 슬롯 검사가 null에 또 걸리면 같은 필드로 메시지가 두 번 나간다
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("필수");
    }

    @Test
    @DisplayName("목표 거리가 비면 필수 위반 하나만 낸다")
    void nullDistanceReportsOnlyRequired() {
        // given & when
        Set<ConstraintViolation<ApplyMatchRequest>> violations =
                validator.validate(new ApplyMatchRequest(DATE.atTime(VALID), null));

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("필수");
    }

    private Set<ConstraintViolation<ApplyMatchRequest>> validate(LocalTime time, Integer distance) {
        return validator.validate(new ApplyMatchRequest(DATE.atTime(time), distance));
    }
}
