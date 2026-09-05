package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
// 소개글·프로필 사진·비밀번호가 이 행에 각각 쓴다. 전체 컬럼을 실으면 서로의 변경을 옛 값으로 덮는다
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 다른 객체에서 생성되지 않도록 하는 속성
public class UserJpaEntity extends BaseTimeEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "alert_consent", nullable = false)
    private boolean alertConsent;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false, length = 20)
    private ProfileVisibility profileVisibility;

    @Column(name = "profile_image_key", length = 255)
    private String profileImageKey;

    @Column(name = "introduction", length = 100)
    private String introduction;

    private UserJpaEntity(
            UUID userId,
            String email,
            String passwordHash,
            boolean alertConsent,
            String profileImageKey,
            ProfileVisibility profileVisibility,
            String introduction
    ) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.alertConsent = alertConsent;
        this.profileImageKey = profileImageKey;
        this.profileVisibility = profileVisibility;
        this.introduction = introduction;
    }

    public static UserJpaEntity create(
            UUID id,
            String email,
            String passwordHash,
            boolean alertConsent,
            String profileImageKey,
            ProfileVisibility profileVisibility,
            String introduction
    ) {
        return new UserJpaEntity(
                id,
                email,
                passwordHash,
                alertConsent,
                profileImageKey,
                profileVisibility,
                introduction
        );
    }

    public void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    public void changeIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeAlertConsent(boolean alertConsent) {
        this.alertConsent = alertConsent;
    }

    public void changeProfileVisibility(ProfileVisibility profileVisibility) {
        this.profileVisibility = profileVisibility;
    }
}
