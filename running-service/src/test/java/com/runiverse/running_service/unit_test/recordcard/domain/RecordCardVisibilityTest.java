package com.runiverse.running_service.unit_test.recordcard.domain;

import com.runiverse.running_service.domain.common.vo.RelationLevel;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기록 카드 공개 범위 단위 테스트")
class RecordCardVisibilityTest {

    // api-spec 8-1 「누가 볼 수 있나」 표를 그대로 옮긴다 — 3 공개 범위 × 4 관계 등급
    @ParameterizedTest(name = "{0} 카드를 {1}가 보면 {2}")
    @CsvSource({
            "PUBLIC,  SELF,    true",
            "PUBLIC,  FRIEND,  true",
            "PUBLIC,  OTHER,   true",
            "PUBLIC,  BLOCKED, false",
            "FRIENDS, SELF,    true",
            "FRIENDS, FRIEND,  true",
            "FRIENDS, OTHER,   false",
            "FRIENDS, BLOCKED, false",
            "PRIVATE, SELF,    true",
            "PRIVATE, FRIEND,  false",
            "PRIVATE, OTHER,   false",
            "PRIVATE, BLOCKED, false"
    })
    @DisplayName("공개 범위마다 볼 수 있는 관계 등급이 정해져 있고, 차단은 어느 범위도 볼 수 없다")
    void visibleTo(RecordCardVisibility visibility, RelationLevel level, boolean expected) {
        assertThat(visibility.isVisibleTo(level)).isEqualTo(expected);
    }
}
