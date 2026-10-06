package com.runiverse.running_service.unit_test.infrastructure.persistence.user;

import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.infrastructure.persistence.user.UserJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UserJpaEntityTest {

    @Test
    @DisplayName("사용자 JPA 엔티티를 생성한다")
    void createUserJpaEntity() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        UserJpaEntity entity = UserJpaEntity.create(
                userId,
                "test@example.com",
                "hashed-password",
                false,
                "profiles/" + userId + "/photo.jpg",
                ProfileVisibility.PUBLIC,
                "러닝을 종료합니다"
        );

        // then
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getEmail()).isEqualTo("test@example.com");
        assertThat(entity.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(entity.isAlertConsent()).isFalse();
        assertThat(entity.getProfileImageKey()).isEqualTo("profiles/" + userId + "/photo.jpg");
        assertThat(entity.getProfileVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
        assertThat(entity.getIntroduction()).isEqualTo("러닝을 종료합니다");
    }
}
