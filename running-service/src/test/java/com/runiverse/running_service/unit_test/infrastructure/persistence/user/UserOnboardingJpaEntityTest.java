package com.runiverse.running_service.unit_test.infrastructure.persistence.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import com.runiverse.running_service.infrastructure.persistence.user.UserOnboardingJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UserOnboardingJpaEntityTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1999, 5, 20);
    private static final BigDecimal WEIGHT = new BigDecimal("70.5");
    private static final BigDecimal HEIGHT = new BigDecimal("175.0");

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("온보딩 JPA 엔티티를 생성한다")
        void createUserOnboardingJpaEntity() {
            // given
            UUID userId = UuidCreator.getTimeOrderedEpoch();

            // when
            UserOnboardingJpaEntity entity = UserOnboardingJpaEntity.create(
                    userId, "러너킴", Gender.MALE, BIRTHDAY, 330, WEIGHT, HEIGHT
            );

            // then
            assertThat(entity.getUserId()).isEqualTo(userId);
            assertThat(entity.getNickname()).isEqualTo("러너킴");
            assertThat(entity.getGender()).isEqualTo(Gender.MALE);
            assertThat(entity.getBirthday()).isEqualTo(BIRTHDAY);
            assertThat(entity.getAvgPace()).isEqualTo(330);
            assertThat(entity.getWeight()).isEqualByComparingTo(WEIGHT);
            assertThat(entity.getHeight()).isEqualByComparingTo(HEIGHT);
        }

        @Test
        @DisplayName("타임스탬프는 영속화 시점에 채워지므로 생성 직후에는 비어 있다")
        void timestampsAreNullBeforePersist() {
            // when
            UserOnboardingJpaEntity entity = UserOnboardingJpaEntity.create(
                    UuidCreator.getTimeOrderedEpoch(), "러너킴", Gender.MALE,
                    BIRTHDAY, 330, WEIGHT, HEIGHT
            );

            // then -> Hibernate가 채우므로 여기서 null인 것이 정상이다
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("매핑 테스트")
    class MappingTest {

        @Test
        @DisplayName("user_id가 PK이며 수정할 수 없다")
        void userIdIsImmutablePrimaryKey() throws NoSuchFieldException {
            // given
            Field field = UserOnboardingJpaEntity.class.getDeclaredField("userId");

            // when & then -> shared-PK 구조라 user_id는 users의 FK이기도 하다
            assertThat(field.isAnnotationPresent(Id.class)).isTrue();
            assertThat(field.getAnnotation(Column.class).updatable()).isFalse();
        }

        @Test
        @DisplayName("프로필 항목은 수정 가능해야 한다")
        void profileColumnsAreUpdatable() throws NoSuchFieldException {
            // when & then -> updatable=false가 붙으면 프로필 수정이 조용히 무시된다
            for (String name : new String[]{"nickname", "gender", "birthday", "avgPace", "weight", "height"}) {
                Field field = UserOnboardingJpaEntity.class.getDeclaredField(name);
                assertThat(field.getAnnotation(Column.class).updatable())
                        .as("%s 는 수정 가능해야 한다", name)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("몸무게와 키는 소수 첫째 자리까지 저장한다")
        void decimalColumnsMatchValueObjectScale() throws NoSuchFieldException {
            // when & then -> VO의 setScale(1)과 어긋나면 저장 시 값이 잘린다
            for (String name : new String[]{"weight", "height"}) {
                Column column = UserOnboardingJpaEntity.class.getDeclaredField(name).getAnnotation(Column.class);
                assertThat(column.precision()).as("%s precision", name).isEqualTo(4);
                assertThat(column.scale()).as("%s scale", name).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("닉네임 컬럼 길이가 VO의 최대 길이와 같다")
        void nicknameLengthMatchesValueObject() throws NoSuchFieldException {
            // when & then -> VO는 통과했는데 DB가 자르면 데이터가 손상된다
            Column column = UserOnboardingJpaEntity.class.getDeclaredField("nickname").getAnnotation(Column.class);
            assertThat(column.length()).isEqualTo(16);
        }

        @Test
        @DisplayName("타임스탬프는 BaseTimeEntity에서 상속받는다")
        void inheritsTimestampsFromBaseEntity() {
            // when & then -> 어노테이션 자체는 BaseTimeEntityTest가 검증한다
            assertThat(UserOnboardingJpaEntity.class.getSuperclass()).isEqualTo(BaseTimeEntity.class);
        }
    }

    @Nested
    @DisplayName("제약 테스트")
    class ConstraintTest {

        @Test
        @DisplayName("닉네임에 유니크 제약이 걸려 있다")
        void nicknameHasUniqueConstraint() {
            // when
            UniqueConstraint[] constraints =
                    UserOnboardingJpaEntity.class.getAnnotation(Table.class).uniqueConstraints();

            // then -> 애플리케이션 중복 검사만으로는 동시 요청에 뚫린다
            assertThat(constraints).hasSize(1);
            assertThat(constraints[0].columnNames()).containsExactly("nickname");
        }

        @Test
        @DisplayName("도메인 경계값과 같은 CHECK 제약이 걸려 있다")
        void checkConstraintsMatchDomainBounds() {
            // when
            String[] constraints = Arrays.stream(
                            UserOnboardingJpaEntity.class.getAnnotation(Checks.class).value())
                    .map(Check::constraints)
                    .toArray(String[]::new);

            // then -> VO의 MIN/MAX를 바꾸면 여기도 같이 바꿔야 한다
            assertThat(constraints).contains(
                    "char_length(nickname) between 2 and 16",
                    "gender in ('MALE', 'FEMALE')",
                    "avg_pace between 120 and 1800",
                    "weight between 20.0 and 300.0",
                    "height between 20.0 and 300.0"
            );
        }
    }
}
