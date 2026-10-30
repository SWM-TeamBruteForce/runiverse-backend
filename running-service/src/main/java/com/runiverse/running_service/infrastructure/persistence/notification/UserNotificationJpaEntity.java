package com.runiverse.running_service.infrastructure.persistence.notification;

import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "user_notifications",
        // 알림함 최신순 — ID가 생성 순서라 역방향 스캔으로 최신순·커서를 처리한다. 역방향이라 DESC를 붙이지 않는다.
        // 안 읽은 수·모두 읽음용 부분 인덱스(WHERE is_read = false)는 JPA로 표현하지 못해 운영 SQL에만 있다
        indexes = @Index(name = "idx_user_notification_list", columnList = "user_id, user_notification_id")
)
@Check(name = "ck_user_notification_type",
        constraints = "type in ('FRIEND_REQUEST_RECEIVED', 'FRIEND_REQUEST_ACCEPTED', 'RUNNING_INVITATION_RECEIVED')")
// 키 검증은 도메인이 한다 — DB는 객체인지만 본다
@Check(name = "ck_user_notification_payload", constraints = "jsonb_typeof(payload) = 'object'")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_notification_id", nullable = false, updatable = false)
    private Long userNotificationId;

    // 받는 사람
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private UserNotificationType type;

    // 종류별 값 — 키 이름(snake_case)은 저장된 약속이라 바꾸지 않는다. 변환은 UserNotificationPayloadMapper가 한다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    // 모두 읽음은 벌크 UPDATE라 이 필드를 거치지 않는다
    @Column(name = "is_read", nullable = false)
    private boolean read;

    private UserNotificationJpaEntity(UUID userId, UserNotificationType type, Map<String, Object> payload) {
        this.userId = userId;
        this.type = type;
        this.payload = payload;
        this.read = false;
    }

    public static UserNotificationJpaEntity create(UUID userId, UserNotificationType type,
                                                   Map<String, Object> payload) {
        return new UserNotificationJpaEntity(userId, type, payload);
    }

    // FK 제약 — 받는 사람이 탈퇴하면 받은 알림이 함께 지워진다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_notification_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserJpaEntity user;
}
