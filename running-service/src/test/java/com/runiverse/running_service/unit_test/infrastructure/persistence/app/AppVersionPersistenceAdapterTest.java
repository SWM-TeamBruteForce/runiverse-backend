package com.runiverse.running_service.unit_test.infrastructure.persistence.app;

import com.runiverse.running_service.domain.app.AppVersion;
import com.runiverse.running_service.domain.app.vo.VersionName;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.infrastructure.persistence.app.AppVersionJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.app.AppVersionPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 어댑터의 분기·변환만 검증한다 —
// JPQL 자체는 실행되지 않는다(UserPersistenceAdapterTest와 같은 한계)
@ExtendWith(MockitoExtension.class)
@DisplayName("앱 버전 저장 어댑터 단위 테스트")
class AppVersionPersistenceAdapterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<AppVersionJpaEntity> query;

    @InjectMocks
    private AppVersionPersistenceAdapter appVersionPersistenceAdapter;

    @Test
    @DisplayName("플랫폼 행을 도메인으로 바꿔 돌려준다")
    void loadsAppVersionOfPlatform() {
        // given
        givenQueryResult(List.of(entity(Platform.IOS, "1.1.0", "1.2.0")));

        // when
        Optional<AppVersion> appVersion = appVersionPersistenceAdapter.load(Platform.IOS);

        // then
        verify(query).setParameter("platform", Platform.IOS);
        assertThat(appVersion).hasValueSatisfying(loaded -> {
            assertThat(loaded.getPlatform()).isEqualTo(Platform.IOS);
            assertThat(loaded.getMinimumVersion()).isEqualTo(new VersionName("1.1.0"));
            assertThat(loaded.getLatestVersion()).isEqualTo(new VersionName("1.2.0"));
        });
    }

    @Test
    @DisplayName("행이 없으면 비어 있다")
    void emptyWhenNoRow() {
        // given
        givenQueryResult(List.of());

        // when & then
        assertThat(appVersionPersistenceAdapter.load(Platform.ANDROID)).isEmpty();
    }

    // getResultList()만 심는다 — 트랜잭션 밖에서 커서가 먼저 닫히는 getResultStream()으로 바꾸면 위 테스트가 깨진다
    private void givenQueryResult(List<AppVersionJpaEntity> result) {
        given(entityManager.createQuery(anyString(), eq(AppVersionJpaEntity.class))).willReturn(query);
        given(query.setParameter(anyString(), any())).willReturn(query);
        given(query.getResultList()).willReturn(result);
    }

    // 행은 배포 SQL이 넣어 엔티티에 생성 메서드가 없다 — DB에서 읽어 온 행을 흉내 내려면 값을 심어야 한다
    private static AppVersionJpaEntity entity(Platform platform, String minimumVersion, String latestVersion) {
        AppVersionJpaEntity entity = BeanUtils.instantiateClass(AppVersionJpaEntity.class);
        ReflectionTestUtils.setField(entity, "platform", platform);
        ReflectionTestUtils.setField(entity, "minimumVersion", minimumVersion);
        ReflectionTestUtils.setField(entity, "latestVersion", latestVersion);
        return entity;
    }
}
