package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.match.common.MatchProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 슬롯 설정 검증은 MatchSlotPolicyTest가 본다 — 여기는 예약 오프셋끼리의 순서만 본다
@DisplayName("매칭 예약 오프셋 설정 검증 단위 테스트")
class MatchPropertiesTest {

    private static final String REMINDER_ORDER_MESSAGE = "리마인더 오프셋은 시작 통지 오프셋보다 크고 마감 오프셋보다 작아야 한다";

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
    @DisplayName("운영 설정(마감 10분·리마인더 5분·통지 10초)은 통과한다")
    void operatingValuesPass() {
        // when & then
        assertThat(violationsOf(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10))).isEmpty();
    }

    @Test
    @DisplayName("리마인더가 마감과 같거나 이르면 거절한다")
    void reminderMustBeAfterClose() {
        // when & then -> 마감 전에 보내면 그 뒤에 들어온 참가자가 못 받는다
        assertThat(violationsOf(Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofSeconds(10)))
                .containsExactly(REMINDER_ORDER_MESSAGE);
        assertThat(violationsOf(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofSeconds(10)))
                .containsExactly(REMINDER_ORDER_MESSAGE);
    }

    @Test
    @DisplayName("리마인더가 시작 통지와 같거나 늦으면 거절한다")
    void reminderMustBeBeforeReady() {
        // when & then -> 통지 뒤면 이미 카운트다운 중이다
        assertThat(violationsOf(Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofSeconds(10)))
                .containsExactly(REMINDER_ORDER_MESSAGE);
        assertThat(violationsOf(Duration.ofMinutes(10), Duration.ofSeconds(5), Duration.ofSeconds(10)))
                .containsExactly(REMINDER_ORDER_MESSAGE);
    }

    @Test
    @DisplayName("오프셋이 빠지면 필수 위반만 낸다")
    void nullReportsOnlyRequired() {
        // when & then -> 순서 검사가 null에 또 걸리면 원인이 가려진다
        assertThat(violationsOf(Duration.ofMinutes(10), null, Duration.ofSeconds(10))).hasSize(1);
    }

    // 오프셋과 무관한 값은 운영 설정 그대로 둔다
    private static List<String> violationsOf(Duration closeOffset, Duration reminderOffset, Duration readyOffset) {
        MatchProperties properties = new MatchProperties(closeOffset, reminderOffset, readyOffset,
                Duration.ofHours(6), 10, Duration.ofMinutes(20),
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
        return validator.validate(properties).stream().map(ConstraintViolation::getMessage).toList();
    }
}
