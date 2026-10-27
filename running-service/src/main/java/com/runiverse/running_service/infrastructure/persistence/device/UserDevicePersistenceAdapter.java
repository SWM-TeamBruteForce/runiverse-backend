package com.runiverse.running_service.infrastructure.persistence.device;

import com.runiverse.running_service.application.device.port.out.DeactivateStaleDevicePort;
import com.runiverse.running_service.application.device.port.out.DeactivateUserDevicesPort;
import com.runiverse.running_service.application.device.port.out.DeleteUserDevicesPort;
import com.runiverse.running_service.application.device.port.out.LoadPushTargetPort;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import com.runiverse.running_service.application.device.port.out.UpsertUserDevicePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.device.UserDevice;
import com.runiverse.running_service.domain.device.vo.DeviceId;
import com.runiverse.running_service.domain.device.vo.PushToken;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserDevicePersistenceAdapter implements UpsertUserDevicePort, DeactivateUserDevicesPort,
        DeleteUserDevicesPort, LoadPushTargetPort, DeactivateStaleDevicePort {

    // JPQL에는 ON CONFLICT가 없어 네이티브로 쓴다. 같은 기기의 요청이 겹쳐도 UNIQUE 위반 없이 나중 것이 덮어쓴다
    private static final String UPSERT = """
            INSERT INTO user_devices (user_id, push_token, platform, device_id, app_version, is_active,
                                      created_at, updated_at)
            VALUES (:userId, :pushToken, :platform, :deviceId, :appVersion, TRUE, :now, :now)
            ON CONFLICT (device_id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                push_token = EXCLUDED.push_token,
                platform = EXCLUDED.platform,
                app_version = EXCLUDED.app_version,
                is_active = TRUE,
                updated_at = EXCLUDED.updated_at
            """;

    private final EntityManager entityManager;

    @Override
    public void upsert(UserDevice userDevice) {
        // 저장 시각은 JVM 타임존 기준이다 — SQL의 now()는 DB 세션 타임존이라 다른 행과 어긋난다
        LocalDateTime now = LocalDateTime.now();

        entityManager.createNativeQuery(UPSERT)
                .setParameter("userId", userDevice.getUserId().value())
                .setParameter("pushToken", userDevice.getPushToken().value())
                .setParameter("platform", userDevice.getPlatform().name())
                .setParameter("deviceId", userDevice.getDeviceId().value())
                .setParameter("appVersion", userDevice.getAppVersion().value())
                .setParameter("now", now)
                .executeUpdate();
    }

    // 켜진 기기만 고친다 — 이미 꺼진 행은 잠그지도 수정 시각을 바꾸지도 않는다
    @Override
    public void deactivateOthers(UserId userId, DeviceId deviceId) {
        entityManager.createQuery("""
                        UPDATE UserDeviceJpaEntity d
                        SET d.active = FALSE, d.updatedAt = :now
                        WHERE d.userId = :userId
                          AND d.deviceId <> :deviceId
                          AND d.active = TRUE
                        """)
                .setParameter("now", LocalDateTime.now())
                .setParameter("userId", userId.value())
                .setParameter("deviceId", deviceId.value())
                .executeUpdate();
    }

    @Override
    public void deactivateAll(UserId userId) {
        entityManager.createQuery("""
                        UPDATE UserDeviceJpaEntity d
                        SET d.active = FALSE, d.updatedAt = :now
                        WHERE d.userId = :userId
                          AND d.active = TRUE
                        """)
                .setParameter("now", LocalDateTime.now())
                .setParameter("userId", userId.value())
                .executeUpdate();
    }

    @Override
    public Optional<PushTarget> loadPushTarget(UserId userId) {
        return entityManager.createQuery("""
                        SELECT d
                        FROM UserDeviceJpaEntity d
                        WHERE d.userId = :userId
                          AND d.active = TRUE
                        ORDER BY d.updatedAt DESC
                        """, UserDeviceJpaEntity.class)
                .setParameter("userId", userId.value())
                // 등록이 유저당 하나만 켜 두지만 DB는 강제하지 않는다 — 여럿이면 마지막에 켜진 기기로 보낸다
                .getResultStream()
                .findFirst()
                .map(entity -> new PushTarget(
                        new DeviceId(entity.getDeviceId()), new PushToken(entity.getPushToken())));
    }

    @Override
    public void deactivateStale(DeviceId deviceId, PushToken pushToken) {
        entityManager.createQuery("""
                        UPDATE UserDeviceJpaEntity d
                        SET d.active = FALSE, d.updatedAt = :now
                        WHERE d.deviceId = :deviceId
                          AND d.pushToken = :pushToken
                          AND d.active = TRUE
                        """)
                .setParameter("now", LocalDateTime.now())
                .setParameter("deviceId", deviceId.value())
                .setParameter("pushToken", pushToken.value())
                .executeUpdate();
    }

    @Override
    public void deleteAll(UserId userId) {
        entityManager.createQuery("""
                        DELETE FROM UserDeviceJpaEntity d
                        WHERE d.userId = :userId
                        """)
                .setParameter("userId", userId.value())
                .executeUpdate();
    }
}
