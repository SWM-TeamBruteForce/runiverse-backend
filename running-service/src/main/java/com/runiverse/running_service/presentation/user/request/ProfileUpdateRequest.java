package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

// 부분 수정이라 전부 선택이다. 소개글은 빈 문자열로 지울 수 있어 @NotBlank를 붙이지 않는다
public record ProfileUpdateRequest(
        @Size(
                max = 100,
                message = "소개글은 100자 이하여야 합니다."
        )
        String introduction,

        @Pattern(
                regexp = "^(?i)(MALE|FEMALE)$",
                message = "성별은 MALE 또는 FEMALE이어야 합니다."
        )
        String gender,

        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
        LocalDate birthday,

        @DecimalMin(value = "20.0", message = "몸무게는 20kg 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "몸무게는 300kg 이하여야 합니다.")
        BigDecimal weightKg,

        @DecimalMin(value = "20.0", message = "키는 20cm 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "키는 300cm 이하여야 합니다.")
        BigDecimal heightCm
) {

    // Birthday VO의 하한을 옮긴 값 — 내장 제약에는 날짜 하한이 없어 @AssertTrue로 표현한다
    private static final LocalDate BIRTHDAY_MIN = LocalDate.of(1900, 1, 1);

    // 온보딩과 같은 기준이다 — 가입 후 생년월일을 바꿔 우회할 수 있어 여기도 막는다
    private static final int MIN_AGE = 14;

    @AssertTrue(message = "생년월일은 1900년 1월 1일 이후여야 합니다.")
    public boolean isBirthdayInRange() {
        return birthday == null || !birthday.isBefore(BIRTHDAY_MIN);
    }

    @AssertTrue(message = "만 14세 미만은 서비스를 이용할 수 없습니다.")
    public boolean isOldEnough() {
        return birthday == null || Period.between(birthday, LocalDate.now()).getYears() >= MIN_AGE;
    }
}
