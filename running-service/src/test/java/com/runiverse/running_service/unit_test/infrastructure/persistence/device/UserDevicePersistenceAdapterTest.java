package com.runiverse.running_service.unit_test.infrastructure.persistence.device;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.device.UserDevice;
import com.runiverse.running_service.domain.device.vo.DeviceId;
import com.runiverse.running_service.domain.device.vo.PushToken;
import com.runiverse.running_service.infrastructure.persistence.device.UserDeviceJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.device.UserDevicePersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("유저 기기 영속성 어댑터 단위 테스트")
public class UserDevicePersistenceAdapterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private TypedQuery<UserDeviceJpaEntity> typedQuery;

    @InjectMocks
    private UserDevicePersistenceAdapter adapter;

    private final UUID userId = UuidCreator.getTimeOrderedEpoch();

    @Test
    @DisplayName("기기 ID가 겹치면 주인·토큰·플랫폼·앱 버전을 덮어쓰고 켜는 upsert다")
    void upsertOverwritesOnConflict() {
        // given
        givenNativeQuery();

        // when
        adapter.upsert(UserDevice.register(userId, "device-1", "token-1", Platform.IOS, "1.1.0"));

        // then -> 다른 계정이 쓰던 기기면 주인이 바뀌어야 한다
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("ON CONFLICT (device_id) DO UPDATE SET")
                .contains("user_id = EXCLUDED.user_id")
                .contains("push_token = EXCLUDED.push_token")
                .contains("platform = EXCLUDED.platform")
                .contains("app_version = EXCLUDED.app_version")
                .contains("is_active = TRUE");
        verify(query).setParameter("userId", userId);
        verify(query).setParameter("pushToken", "token-1");
        verify(query).setParameter("platform", "IOS");
        verify(query).setParameter("deviceId", "device-1");
        verify(query).setParameter("appVersion", "1.1.0");
        // 저장 시각은 JVM 타임존 기준이라 SQL의 now()가 아니라 값으로 넘긴다
        verify(query).setParameter(eq("now"), any(LocalDateTime.class));
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("다른 기기 끄기는 이 기기를 빼고 켜진 기기만 고친다")
    void deactivateOthersSkipsThisDevice() {
        // given
        givenJpqlQuery();

        // when
        adapter.deactivateOthers(new UserId(userId), new DeviceId("device-1"));

        // then -> 이미 꺼진 행은 잠그지 않고 수정 시각도 바꾸지 않는다
        String jpql = capturedJpql();
        assertThat(jpql)
                .contains("SET d.active = FALSE, d.updatedAt = :now")
                .contains("d.deviceId <> :deviceId")
                .contains("d.active = TRUE");
        verify(query).setParameter("userId", userId);
        verify(query).setParameter("deviceId", "device-1");
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("전부 끄기는 그 유저의 켜진 기기를 모두 끈다")
    void deactivateAllTurnsOffUsersDevices() {
        // given
        givenJpqlQuery();

        // when
        adapter.deactivateAll(new UserId(userId));

        // then -> 일괄 UPDATE는 @UpdateTimestamp를 거치지 않아 수정 시각을 직접 넣는다
        String jpql = capturedJpql();
        assertThat(jpql)
                .contains("SET d.active = FALSE, d.updatedAt = :now")
                .contains("d.active = TRUE")
                .doesNotContain(":deviceId");
        verify(query).setParameter("userId", userId);
        verify(query, never()).setParameter(eq("deviceId"), any());
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("삭제는 그 유저의 기기 행을 모두 지운다")
    void deleteAllRemovesUsersDevices() {
        // given
        givenJpqlQuery();

        // when
        adapter.deleteAll(new UserId(userId));

        // then -> 꺼진 기기도 개인 데이터라 함께 지운다
        String jpql = capturedJpql();
        assertThat(jpql).contains("DELETE FROM UserDeviceJpaEntity d").doesNotContain("active");
        verify(query).setParameter("userId", userId);
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("푸시 대상은 그 유저의 켜진 기기 중 마지막에 켜진 기기다")
    void loadPushTargetPicksLatestActiveDevice() {
        // given
        UserDeviceJpaEntity entity = mock(UserDeviceJpaEntity.class);
        when(entity.getDeviceId()).thenReturn("device-1");
        when(entity.getPushToken()).thenReturn("token-1");
        when(entityManager.createQuery(anyString(), eq(UserDeviceJpaEntity.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getResultStream()).thenReturn(Stream.of(entity));

        // when
        Optional<PushTarget> target = adapter.loadPushTarget(new UserId(userId));

        // then -> 등록이 하나만 켜 두지만 DB가 강제하지 않아, 여럿이어도 한 곳으로만 보낸다
        assertThat(target).contains(new PushTarget(new DeviceId("device-1"), new PushToken("token-1")));
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(UserDeviceJpaEntity.class));
        assertThat(jpql.getValue())
                .contains("d.userId = :userId")
                .contains("d.active = TRUE")
                .contains("ORDER BY d.updatedAt DESC");
        verify(typedQuery).setParameter("userId", userId);
    }

    @Test
    @DisplayName("켜진 기기가 없으면 푸시 대상도 없다")
    void loadPushTargetIsEmptyWithoutActiveDevice() {
        // given
        when(entityManager.createQuery(anyString(), eq(UserDeviceJpaEntity.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getResultStream()).thenReturn(Stream.empty());

        // when & then
        assertThat(adapter.loadPushTarget(new UserId(userId))).isEmpty();
    }

    @Test
    @DisplayName("만료 토큰 정리는 그 기기의 토큰이 그대로일 때만 끈다")
    void deactivateStaleRequiresSameToken() {
        // given
        givenJpqlQuery();

        // when
        adapter.deactivateStale(new DeviceId("device-1"), new PushToken("token-1"));

        // then -> 보내는 사이 앱이 새 토큰을 등록했으면 그 기기는 살아 있다
        String jpql = capturedJpql();
        assertThat(jpql)
                .contains("SET d.active = FALSE, d.updatedAt = :now")
                .contains("d.deviceId = :deviceId")
                .contains("d.pushToken = :pushToken")
                .contains("d.active = TRUE");
        verify(query).setParameter("deviceId", "device-1");
        verify(query).setParameter("pushToken", "token-1");
        verify(query).executeUpdate();
    }

    private void givenNativeQuery() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    private void givenJpqlQuery() {
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    private String capturedJpql() {
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture());
        return jpql.getValue();
    }
}
