package com.runiverse.running_service.unit_test.notification.application;

import com.runiverse.running_service.application.notification.common.NotificationProperties;
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

// 설정이 틀리면 기동 때 바인딩이 실패해야 한다 — 운영 중에 모든 알림이 목록에서 사라지지 않게
@DisplayName("알림 설정 검증 단위 테스트")
class NotificationPropertiesTest {

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

    private static List<String> violationsOf(NotificationProperties properties) {
        return validator.validate(properties).stream().map(ConstraintViolation::getMessage).toList();
    }

    @Test
    @DisplayName("운영 설정(보관 30일)은 통과한다")
    void operatingValuePasses() {
        // when & then
        assertThat(violationsOf(new NotificationProperties(Duration.ofDays(30)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("보관 기간이 0보다 크지 않으면 거절한다")
    void retentionMustBePositive(long days) {
        // when & then -> 0이면 방금 쌓인 알림도 목록에서 빠진다
        assertThat(violationsOf(new NotificationProperties(Duration.ofDays(days))))
                .containsExactly("알림 보관 기간은 0보다 커야 한다");
    }

    @Test
    @DisplayName("값이 빠지면 @NotNull만 보고하고 교차 검증은 끼어들지 않는다")
    void nullIsReportedByNotNullOnly() {
        // when & then -> 교차 검증이 NPE로 터지면 원인이 가려진다
        assertThat(violationsOf(new NotificationProperties(null))).hasSize(1);
    }
}
