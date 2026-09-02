package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

public record OnboardingRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 16,
                message = "닉네임은 2자 이상 16자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9_]+$",
                message = "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."
        )
        String nickname,

        @NotBlank(message = "성별은 필수입니다.")
        @Pattern(
                regexp = "^(?i)(MALE|FEMALE)$",
                message = "성별은 MALE 또는 FEMALE이어야 합니다."
        )
        String gender,

        @NotNull(message = "생년월일은 필수입니다.")
        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
        LocalDate birthday,

        @NotNull(message = "평균 페이스는 필수입니다.")
        @Min(value = 120, message = "평균 페이스는 120초 이상이어야 합니다.")
        @Max(value = 1800, message = "평균 페이스는 1800초 이하여야 합니다.")
        Integer averagePaceSecondsPerKm,

        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "20.0", message = "몸무게는 20kg 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "몸무게는 300kg 이하여야 합니다.")
        BigDecimal weightKg,

        @NotNull(message = "키는 필수입니다.")
        @DecimalMin(value = "20.0", message = "키는 20cm 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "키는 300cm 이하여야 합니다.")
        BigDecimal heightCm
) {

    // Birthday VO의 하한을 옮긴 값 — 내장 제약에는 날짜 하한이 없어 @AssertTrue로 표현한다
    private static final LocalDate BIRTHDAY_MIN = LocalDate.of(1900, 1, 1);

    // 만 14세 미만은 법정대리인 동의 없이 개인정보를 처리할 수 없어 받지 않는다.
    // Birthday VO가 아니라 여기 두는 이유는 도메인 불변식이 아니라 정책이라서다 —
    // 1900년 하한은 바뀔 일이 없지만 연령 기준은 바뀔 수 있다
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
