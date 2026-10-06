package com.runiverse.running_service.infrastructure.persistence.user;

import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "delete_users",
        // 탈퇴자 신원 조회(email 등가)와 보관 기간 만료 배치가 함께 탄다.
        // 배포 스키마는 WHERE email IS NOT NULL 부분 인덱스다 — @Index로는 표현할 수 없다
        indexes = @Index(name = "idx_delete_user_pending", columnList = "email, created_at")
)
@Check(name = "ck_delete_user_gender", constraints = "gender is null or gender in ('MALE', 'FEMALE')")
@Check(name = "ck_delete_user_login_type",
        constraints = "login_type in ('LOCAL', 'GOOGLE', 'KAKAO')")
@Check(name = "ck_delete_user_birth_year", constraints = "birth_year is null or birth_year >= 1900")
@Check(name = "ck_delete_user_avg_pace",
        constraints = "avg_pace is null or avg_pace between 120 and 1800")
@Check(name = "ck_delete_user_bmi", constraints = "bmi is null or bmi > 0")
// 온보딩 스냅샷은 함께 차고 함께 빈다. nickname은 90일 뒤 혼자 비워지므로 묶지 않는다
@Check(name = "ck_delete_user_onboarding_snapshot",
        constraints = "(gender is null and birth_year is null"
                + " and avg_pace is null and bmi is null)"
                + " or (gender is not null and birth_year is not null"
                + " and avg_pace is not null and bmi is not null)")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeleteUserJpaEntity extends BaseCreatedAtEntity {

    // 논리 참조 — users를 하드 삭제한 뒤에도 값이 남아야 해서 FK를 걸지 않는다
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "nickname", length = 16)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "avg_pace")
    private Integer avgPace;

    @Column(name = "bmi", precision = 5, scale = 1)
    private BigDecimal bmi;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_type", nullable = false, length = 10)
    private LoginType loginType;

    // users.created_at이다 — 온보딩 완료 시각이 아니다
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    private DeleteUserJpaEntity(UUID userId, String email, String nickname, Gender gender,
                                Integer birthYear, Integer avgPace, BigDecimal bmi,
                                LoginType loginType, LocalDateTime joinedAt) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.gender = gender;
        this.birthYear = birthYear;
        this.avgPace = avgPace;
        this.bmi = bmi;
        this.loginType = loginType;
        this.joinedAt = joinedAt;
    }

    public static DeleteUserJpaEntity create(UUID userId, String email, String nickname,
                                             Gender gender, Integer birthYear, Integer avgPace,
                                             BigDecimal bmi, LoginType loginType,
                                             LocalDateTime joinedAt) {
        return new DeleteUserJpaEntity(userId, email, nickname, gender,
                birthYear, avgPace, bmi, loginType, joinedAt);
    }
}
