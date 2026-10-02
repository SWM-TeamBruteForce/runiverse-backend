package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

public record RunningRecordsRequest(
        @NotBlank(message = "조회 시작일은 필수입니다.")
        String from,
        @NotBlank(message = "조회 종료일은 필수입니다.")
        String to
) {

    // 날짜 타입으로 받으면 2026-09-01Z·다섯 자리 연도까지 통과한다 — 글자로 받아 직접 확인한다
    private static final Pattern DATE_FORMAT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final int MAX_PERIOD_DAYS = 31;   // 양 끝 날짜를 포함해 센 날수

    // 앞 검사에서 걸린 값은 뒤 검사가 통과시킨다 — 한 가지 잘못에 문구가 둘 나가지 않게
    @AssertTrue(message = "입력값이 올바르지 않습니다.")
    public boolean isDateFormatValid() {
        return isBlankOrDate(from) && isBlankOrDate(to);
    }

    @AssertTrue(message = "조회 시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isDateOrderValid() {
        LocalDate start = toDate(from);
        LocalDate end = toDate(to);
        return start == null || end == null || !start.isAfter(end);
    }

    @AssertTrue(message = "조회 기간은 31일 이하여야 합니다.")
    public boolean isDateRangeValid() {
        LocalDate start = toDate(from);
        LocalDate end = toDate(to);
        return start == null || end == null || start.isAfter(end)
                || ChronoUnit.DAYS.between(start, end) < MAX_PERIOD_DAYS;
    }

    private static boolean isBlankOrDate(String value) {
        return value == null || value.isBlank() || toDate(value) != null;
    }

    // 형식이 다르거나 없는 날짜(2월 30일)면 null
    private static LocalDate toDate(String value) {
        if (value == null || !DATE_FORMAT.matcher(value).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
