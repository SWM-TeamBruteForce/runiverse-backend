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

    // ApplyMatchRequest의 상수와 짝이다 — 거기를 바꾸면 여기 셋도 같이 바꾼다
    private static final LocalTime EARLIEST = LocalTime.of(18, 0);
    private static final LocalTime LATEST = LocalTime.of(22, 0);
    private static final int SLOT_MINUTES = 30;

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

    @Test
    @DisplayName("허용 창의 양 끝은 통과한다")
    void boundaryTimesPass() {
        // given & when & then -> 경계를 포함한다. 배타로 바뀌면 마지막 슬롯이 사라진다
        assertThat(validate(EARLIEST, DISTANCE)).isEmpty();
        assertThat(validate(LATEST, DISTANCE)).isEmpty();
    }

    @Test
    @DisplayName("창 안에서 간격에 맞는 시각은 통과한다")
    void alignedTimePasses() {
        // given -> 창 한가운데의 정렬된 시각
        LocalTime aligned = EARLIEST.plusMinutes(SLOT_MINUTES * 3L);

        // when & then
        assertThat(validate(aligned, DISTANCE)).isEmpty();
    }

    @Test
    @DisplayName("창을 벗어난 시각은 거절한다")
    void timeOutsideWindowFails() {
        // given -> 마지막 슬롯 바로 다음 칸
        LocalTime tooLate = LATEST.plusMinutes(SLOT_MINUTES);

        // when & then
        assertThat(validate(tooLate, DISTANCE)).hasSize(1);
    }

    @Test
    @DisplayName("간격에 맞지 않는 분은 거절한다")
    void unalignedMinuteFails() {
        // given -> 간격의 배수가 아닌 분
        LocalTime unaligned = EARLIEST.plusMinutes(SLOT_MINUTES + 1L);

        // when & then
        assertThat(validate(unaligned, DISTANCE)).hasSize(1);
    }

    @Test
    @DisplayName("초·나노가 붙으면 거절한다")
    void secondsAndNanosFail() {
        // given -> 분만 보면 통과하지만 슬롯이 아니다
        LocalTime withSeconds = EARLIEST.plusMinutes(SLOT_MINUTES).withSecond(30);
        LocalTime withNanos = EARLIEST.plusMinutes(SLOT_MINUTES).withNano(1);

        // when & then
        assertThat(validate(withSeconds, DISTANCE)).hasSize(1);
        assertThat(validate(withNanos, DISTANCE)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}m")
    @ValueSource(ints = {3_000, 5_000, 10_000})
    @DisplayName("허용된 목표 거리는 통과한다")
    void allowedDistancePasses(int distance) {
        // when & then
        assertThat(validate(EARLIEST, distance)).isEmpty();
    }

    @ParameterizedTest(name = "{0}m")
    @ValueSource(ints = {0, -5_000, 1_000, 4_000, 7_000, 20_000})
    @DisplayName("허용되지 않은 목표 거리는 거절한다")
    void disallowedDistanceFails(int distance) {
        // when & then -> 자유 입력이 아니라 정해진 선택지다
        assertThat(validate(EARLIEST, distance)).hasSize(1);
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
                validator.validate(new ApplyMatchRequest(DATE.atTime(EARLIEST), null));

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("필수");
    }

    private Set<ConstraintViolation<ApplyMatchRequest>> validate(LocalTime time, Integer distance) {
        return validator.validate(new ApplyMatchRequest(DATE.atTime(time), distance));
    }
}
