package com.runiverse.running_service.infrastructure.persistence.relationship;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
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

@Getter
@Entity
@Table(
        name = "user_relationships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_relationship_user_target",
                columnNames = {"user_id", "target_id"}
        ),
        // 상대가 탈퇴할 때 target_id 쪽 CASCADE 삭제가 탄다 — 관계 확인은 (user_id, target_id) UNIQUE가 맡는다
        indexes = {
                @Index(name = "idx_user_relationship_target", columnList = "target_id"),
                // 친구 목록 — 묶음(type)마다 updated_at·target_id 역순으로 읽는다. 역방향 스캔이라 DESC를 붙이지 않는다
                @Index(name = "idx_user_relationship_list", columnList = "user_id, type, updated_at, target_id")
        }
)
@Check(name = "ck_user_relationship_type",
        constraints = "type in ('NONE', 'FRIEND', 'FRIEND_REQUEST_SENT', 'FRIEND_REQUEST_RECEIVED', 'BLOCKED')")
@Check(name = "ck_user_relationship_users", constraints = "user_id <> target_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRelationshipJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_relationship_id", nullable = false, updatable = false)
    private Long userRelationshipId;

    // 행의 주인 — "내 관점"의 나
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    // 관계가 끝나도 행을 지우지 않고 이 값만 NONE으로 바꾼다
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private RelationshipType type;

    private UserRelationshipJpaEntity(UUID userId, UUID targetId, RelationshipType type) {
        this.userId = userId;
        this.targetId = targetId;
        this.type = type;
    }

    public static UserRelationshipJpaEntity create(UUID userId, UUID targetId, RelationshipType type) {
        return new UserRelationshipJpaEntity(userId, targetId, type);
    }

    public void changeType(RelationshipType type) {
        this.type = type;
    }

    // FK 제약 — 어느 쪽이 탈퇴해도 행이 함께 지워진다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_relationship_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserJpaEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "target_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_relationship_target")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserJpaEntity target;
}
