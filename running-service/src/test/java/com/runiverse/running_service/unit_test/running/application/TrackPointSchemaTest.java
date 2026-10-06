package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.port.out.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GPS 트랙 compact 스키마 단위 테스트")
public class TrackPointSchemaTest {

    // 저장할 때만 epoch 초로 바뀐다 — 이름이 달라지는 유일한 필드다
    private static final String RENAMED_COMPONENT = "recordedAt";
    private static final String RENAMED_FIELD = "recordedAtEpochSecond";

    private static List<String> recordComponentNames() {
        return Arrays.stream(TrackPoint.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    @Test
    @DisplayName("COMPACT_FIELDS는 TrackPoint의 컴포넌트와 순서까지 일치한다")
    void compactFieldsMatchRecordComponentsInOrder() {
        // given -> 손으로 적은 목록이라 컴포넌트가 늘거나 순서가 바뀌면 조용히 어긋난다
        List<String> expected = recordComponentNames().stream()
                .map(name -> name.equals(RENAMED_COMPONENT) ? RENAMED_FIELD : name)
                .toList();

        // when & then
        assertThat(TrackPoint.COMPACT_FIELDS).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("이름이 달라지는 필드는 recordedAt 하나뿐이다")
    void onlyRecordedAtIsRenamed() {
        // given
        List<String> components = recordComponentNames();

        // when -> 컴포넌트명과 다른 자리를 전부 모은다
        List<String> renamed = TrackPoint.COMPACT_FIELDS.stream()
                .filter(field -> !components.contains(field))
                .toList();

        // then -> 여기가 늘면 S3 봉투를 읽는 쪽이 매핑 규칙을 더 알아야 한다
        assertThat(renamed).containsExactly(RENAMED_FIELD);
    }

    @Test
    @DisplayName("자리 순서 정본은 수정할 수 없다")
    void compactFieldsAreImmutable() {
        // when & then -> 런타임에 순서가 바뀌면 이미 올라간 S3 객체와 어긋난다
        assertThat(TrackPoint.COMPACT_FIELDS).isUnmodifiable();
    }
}
