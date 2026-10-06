package com.runiverse.running_service.unit_test.running.presentation;

import com.runiverse.running_service.presentation.running.request.RunningRecordsRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 날짜를 글자로 받아 DTO가 직접 검사한다 — 400 문구는 전부 여기서 만들어진다
@DisplayName("기록 목록 요청 DTO 검증 단위 테스트")
public class RunningRecordsRequestValidationTest {

    private static final String REQUIRED_FROM = "조회 시작일은 필수입니다.";
    private static final String REQUIRED_TO = "조회 종료일은 필수입니다.";
    private static final String INVALID = "입력값이 올바르지 않습니다.";
    private static final String ORDER = "조회 시작일은 종료일보다 늦을 수 없습니다.";
    private static final String RANGE = "조회 기간은 31일 이하여야 합니다.";

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

    private static List<String> messagesOf(String from, String to) {
        return validator.validate(new RunningRecordsRequest(from, to)).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    @ParameterizedTest(name = "{0} ~ {1}")
    @CsvSource({
            "2026-09-27, 2026-10-03",   // 한 주
            "2026-09-01, 2026-09-01",   // 하루
            "2026-09-01, 2026-10-01",   // 양 끝 포함 31일 — 상한
            "2099-01-01, 2099-01-07",   // 미래
    })
    @DisplayName("한 주·하루·31일·미래 날짜는 통과한다")
    void 유효한_기간은_통과한다(String from, String to) {
        // when & then
        assertThat(messagesOf(from, to)).isEmpty();
    }

    @Test
    @DisplayName("둘 다 비우면 필수 문구 둘만 나온다")
    void 둘_다_비우면_필수_문구만_나온다() {
        // when & then -> 형식·순서·기간 검사가 빈 값에 반응하면 문구가 겹친다
        assertThat(messagesOf(null, null))
                .containsExactlyInAnyOrder(REQUIRED_FROM, REQUIRED_TO);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("시작일이 비면 시작일 필수 문구만 나온다")
    void 시작일이_비면_필수_문구만_나온다(String from) {
        // when & then
        assertThat(messagesOf(from, "2026-09-02")).containsExactly(REQUIRED_FROM);
    }

    @Test
    @DisplayName("종료일이 비면 종료일 필수 문구만 나온다")
    void 종료일이_비면_필수_문구만_나온다() {
        // when & then
        assertThat(messagesOf("2026-09-01", null)).containsExactly(REQUIRED_TO);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "2026-9-1",                  // 자릿수
            "20260901",                  // 구분자 없음
            "2026-02-30",                // 없는 날짜
            "2026-09-01Z",               // 오프셋
            "2026-09-01+09:00",
            "+10000-01-01",              // 다섯 자리 연도
            "-0001-01-01",               // 음수 연도
            "abc",
            " 2026-09-01",               // 앞 공백
            "2026-09-01,2026-09-02",     // 같은 파라미터를 두 번 보내면 쉼표로 합쳐져 들어온다
    })
    @DisplayName("YYYY-MM-DD가 아니거나 없는 날짜면 기본 문구만 나온다")
    void 형식이_다르면_기본_문구만_나온다(String from) {
        // when & then
        assertThat(messagesOf(from, "2026-09-02")).containsExactly(INVALID);
    }

    @Test
    @DisplayName("두 날짜가 다 틀려도 기본 문구는 한 번만 나온다")
    void 두_날짜가_다_틀려도_문구는_한_번이다() {
        // when & then
        assertThat(messagesOf("abc", "xyz")).containsExactly(INVALID);
    }

    @Test
    @DisplayName("형식이 틀린 날짜는 순서·기간을 검사하지 않는다")
    void 형식이_틀리면_순서와_기간은_보지_않는다() {
        // when & then -> 2020년이 뒤에 와도 순서 문구가 겹치지 않는다
        assertThat(messagesOf("abc", "2020-01-01")).containsExactly(INVALID);
    }

    @Test
    @DisplayName("시작일 형식 오류와 종료일 누락은 각자의 문구로 함께 나온다")
    void 형식_오류와_누락은_각자_문구가_나온다() {
        // when & then -> 서로 다른 필드의 잘못이라 둘 다 알려준다
        assertThat(messagesOf("abc", null)).containsExactlyInAnyOrder(INVALID, REQUIRED_TO);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 순서 문구만 나온다")
    void 시작일이_늦으면_순서_문구만_나온다() {
        // when & then -> 거꾸로 된 기간은 31일을 넘어도 기간 문구가 겹치지 않는다
        assertThat(messagesOf("2026-12-31", "2026-01-01")).containsExactly(ORDER);
    }

    @Test
    @DisplayName("양 끝 포함 32일이면 기간 문구만 나온다")
    void 기간이_31일을_넘으면_기간_문구만_나온다() {
        // when & then
        assertThat(messagesOf("2026-09-01", "2026-10-02")).containsExactly(RANGE);
    }
}
