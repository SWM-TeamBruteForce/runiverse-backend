package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.match.common.InviteProperties;
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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 설정이 틀리면 기동 때 바인딩이 실패해야 한다 — 운영 중에 "만들자마자 마감" 같은 방이 생기지 않게
@DisplayName("초대 러닝 설정 검증 단위 테스트")
class InvitePropertiesTest {

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

    private static List<String> violationsOf(InviteProperties properties) {
        return validator.validate(properties).stream().map(ConstraintViolation::getMessage).toList();
    }

    @Test
    @DisplayName("운영 설정(10분·1분·1~42.195km·5회)은 통과한다")
    void operatingValuesPass() {
        // when & then
        assertThat(violationsOf(new InviteProperties(
                Duration.ofMinutes(10), Duration.ofMinutes(1), 1_000, 42_195, 5))).isEmpty();
    }

    @Test
    @DisplayName("최소 준비 시간이 마감 오프셋보다 길지 않으면 거절한다")
    void leadTimeMustExceedCloseOffset() {
        // when & then -> 같으면 방이 만들어지는 순간 이미 마감이다
        assertThat(violationsOf(new InviteProperties(
                Duration.ofMinutes(1), Duration.ofMinutes(1), 1_000, 42_195, 5)))
                .containsExactly("최소 준비 시간은 초대 마감 오프셋보다 길어야 한다");
    }

    @Test
    @DisplayName("마감 오프셋이 0이면 거절한다")
    void closeOffsetMustBePositive() {
        // when & then
        assertThat(violationsOf(new InviteProperties(
                Duration.ofMinutes(10), Duration.ZERO, 1_000, 42_195, 5)))
                .containsExactly("초대 마감 오프셋은 0보다 커야 한다");
    }

    @Test
    @DisplayName("최소 거리가 최대 거리보다 크면 거절한다")
    void distanceRangeMustBeOrdered() {
        // when & then
        assertThat(violationsOf(new InviteProperties(
                Duration.ofMinutes(10), Duration.ofMinutes(1), 5_000, 3_000, 5)))
                .containsExactly("최소 거리는 최대 거리보다 클 수 없다");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("보낸 횟수 상한이 1 미만이면 거절한다")
    void maxInviteCountMustBePositive(int maxInviteCount) {
        // when & then -> 0이면 처음 보내는 초대도 건너뛰어 아무도 부를 수 없다
        assertThat(violationsOf(new InviteProperties(
                Duration.ofMinutes(10), Duration.ofMinutes(1), 1_000, 42_195, maxInviteCount)))
                .hasSize(1);
    }

    @Test
    @DisplayName("값이 빠지면 각 필드가 따로 보고하고 교차 검증은 끼어들지 않는다")
    void nullsAreReportedByNotNullOnly() {
        // when & then -> 교차 검증이 NPE로 터지면 원인이 가려진다
        assertThat(violationsOf(new InviteProperties(null, null, null, null, null))).hasSize(5);
    }
}
