package com.runiverse.running_service.domain.app.vo;

import com.runiverse.running_service.domain.app.exception.InvalidVersionNameFormatException;
import com.runiverse.running_service.domain.app.exception.VersionNameRequiredException;

import java.util.Arrays;
import java.util.regex.Pattern;

// 스토어에 표시되는 버전 이름이다 — 업로드 순번인 빌드 번호가 아니다
public record VersionName(String value) {

    // 부분마다 9자리까지 — 정수로 바꿀 때 넘치지 않는다
    private static final Pattern FORMAT = Pattern.compile("^[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}$");

    public VersionName {
        if (value == null) {
            throw new VersionNameRequiredException();
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidVersionNameFormatException();
        }
    }

    // 부분마다 정수로 비교한다 — 글자로 비교하면 1.10.0이 1.9.0보다 낮게 나온다
    public boolean isLowerThan(VersionName other) {
        return Arrays.compare(parts(), other.parts()) < 0;
    }

    private int[] parts() {
        return Arrays.stream(value.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }
}
