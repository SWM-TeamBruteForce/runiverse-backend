package com.runiverse.running_service.infrastructure.persistence.app;

import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.infrastructure.persistence.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

// 도메인 테이블이 아니라 운영 설정 테이블이다 — 행은 배포 SQL이 넣고 값은 운영이 SQL로 바꾼다.
// 앱은 읽기만 해서 생성 메서드를 두지 않는다. 애플리케이션 검증을 거치지 않으므로 형식·대소 관계를 DB가 막는다
@Getter
@Entity
@Table(
        name = "app_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_app_version_platform",
                columnNames = "platform"
        )
)
@Check(name = "ck_app_version_platform", constraints = "platform in ('IOS', 'ANDROID')")
// VersionName과 같은 규칙(부분마다 9자리까지) — DB가 받은 값은 서버도 읽을 수 있어야 한다
@Check(name = "ck_app_version_format",
        constraints = "minimum_version ~ '^[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}$' "
                + "and latest_version ~ '^[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}$'")
// 부분마다 정수로 비교한다 — 글자로 비교하면 1.10.0이 1.9.0보다 낮다
@Check(name = "ck_app_version_order",
        constraints = "string_to_array(minimum_version, '.')::int[] "
                + "<= string_to_array(latest_version, '.')::int[]")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppVersionJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "app_version_id")
    private Long appVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private Platform platform;

    @Column(name = "minimum_version", nullable = false)
    private String minimumVersion;

    @Column(name = "latest_version", nullable = false)
    private String latestVersion;
}
