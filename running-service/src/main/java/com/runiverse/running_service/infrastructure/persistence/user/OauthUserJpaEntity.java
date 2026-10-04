package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "oauth_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oauth_user_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@Check(name = "ck_oauth_user_provider", constraints = "provider in ('KAKAO', 'GOOGLE')")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthUserJpaEntity extends BaseTimeEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_id", nullable = false, updatable = false, length = 255)
    private String providerId;

    private OauthUserJpaEntity(UUID userId, Provider provider, String providerId) {
        this.userId = userId;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static OauthUserJpaEntity create(UUID userId, Provider provider, String providerId) {
        return new OauthUserJpaEntity(userId, provider, providerId);
    }

    // FK 제약
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_oauth_user_users")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserJpaEntity user;
}
