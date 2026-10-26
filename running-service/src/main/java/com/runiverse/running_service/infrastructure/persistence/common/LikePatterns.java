package com.runiverse.running_service.infrastructure.persistence.common;

public final class LikePatterns {

    private LikePatterns() {
    }

    // 검색어가 들어간 값을 찾는 패턴이다 — 쿼리에 ESCAPE '\'를 함께 써야 %·_가 글자로 읽힌다
    public static String containing(String value) {
        return "%" + escape(value) + "%";
    }

    // 이스케이프 문자 자체를 먼저 바꿔야 두 번 바뀌지 않는다
    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
