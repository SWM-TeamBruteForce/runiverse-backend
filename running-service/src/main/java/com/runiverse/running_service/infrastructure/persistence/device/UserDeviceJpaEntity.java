package com.runiverse.running_service.infrastructure.persistence.device;

import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import com.runiverse.running_service.infrastructure.persistence.user.UserJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

// 행은 어댑터가 네이티브 upsert로만 넣어 생성 메서드를 두지 않는다
@Getter
@Entity
@Table(
        name = "user_devices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_device_device_id",
                columnNames = "device_id"
        ),
        // 등록·로그인·로그아웃 때 유저의 기기를 끄고, 탈퇴 때 지운다
        indexes = @Index(name = "idx_user_device_user", columnList = "user_id")
)
@Check(name = "ck_user_device_platform", constraints = "platform in ('IOS', 'ANDROID')")
// VersionName과 같은 규칙(부분마다 9자리까지) — DB가 받은 값은 서버도 읽을 수 있어야 한다
@Check(name = "ck_user_device_app_version",
        constraints = "app_version ~ '^[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}$'")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeviceJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_device_id", nullable = false, updatable = false)
    private Long userDeviceId;

    // 지금 이 기기로 로그인한 유저 — 다른 계정이 등록하면 바뀐다
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // FCM 토큰은 공식 길이 상한이 없다
    @Column(name = "push_token", nullable = false, columnDefinition = "text")
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private Platform platform;

    @Column(name = "device_id", nullable = false, updatable = false, length = 100)
    private String deviceId;

    @Column(name = "app_version", nullable = false, length = 30)
    private String appVersion;

    // 푸시를 받는 기기인가 — 유저당 하나만 켜진다
    @Column(name = "is_active", nullable = false)
    private boolean active;

    // FK 제약 — 탈퇴하면 행이 함께 지워진다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_device_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserJpaEntity user;
}
