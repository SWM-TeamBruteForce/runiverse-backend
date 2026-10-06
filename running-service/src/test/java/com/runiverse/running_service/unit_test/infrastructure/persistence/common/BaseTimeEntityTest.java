package com.runiverse.running_service.unit_test.infrastructure.persistence.common;

import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseTimeEntityTest {

    @Test
    @DisplayName("created_at에 생성 시각 어노테이션이 붙어 있다")
    void createdAtHasCreationTimestamp() throws NoSuchFieldException {
        // when & then -> 없으면 NOT NULL 위반으로 저장 자체가 실패한다
        Field createdAt = BaseCreatedAtEntity.class.getDeclaredField("createdAt");

        assertThat(createdAt.isAnnotationPresent(CreationTimestamp.class)).isTrue();
        assertThat(createdAt.getAnnotation(Column.class).name()).isEqualTo("created_at");
    }

    @Test
    @DisplayName("생성 시각은 수정할 수 없다")
    void createdAtIsNotUpdatable() throws NoSuchFieldException {
        // when & then -> updatable을 열어두면 수정 시 생성 시각이 덮인다
        Field createdAt = BaseCreatedAtEntity.class.getDeclaredField("createdAt");

        assertThat(createdAt.getAnnotation(Column.class).updatable()).isFalse();
    }

    @Test
    @DisplayName("updated_at에 수정 시각 어노테이션이 붙어 있다")
    void updatedAtHasUpdateTimestamp() throws NoSuchFieldException {
        // when & then
        Field updatedAt = BaseTimeEntity.class.getDeclaredField("updatedAt");

        assertThat(updatedAt.isAnnotationPresent(UpdateTimestamp.class)).isTrue();
        assertThat(updatedAt.getAnnotation(Column.class).name()).isEqualTo("updated_at");
    }

    @Test
    @DisplayName("BaseTimeEntity는 생성 시각도 함께 상속한다")
    void baseTimeEntityInheritsCreatedAt() {
        // when & then -> 수정 시각만 있는 테이블은 없다
        assertThat(BaseTimeEntity.class.getSuperclass()).isEqualTo(BaseCreatedAtEntity.class);
    }

    @Test
    @DisplayName("두 베이스 모두 @MappedSuperclass다")
    void bothAreMappedSuperclass() {
        // when & then -> 빠지면 컬럼이 아예 매핑되지 않는다
        assertThat(BaseCreatedAtEntity.class.isAnnotationPresent(MappedSuperclass.class)).isTrue();
        assertThat(BaseTimeEntity.class.isAnnotationPresent(MappedSuperclass.class)).isTrue();
    }
}
