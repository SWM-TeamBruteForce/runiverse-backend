package com.runiverse.running_service.unit_test.infrastructure.sse;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.sse.AppStreamRegistryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@DisplayName("앱 스트림 레지스트리 단위 테스트")
class AppStreamRegistryAdapterTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId OTHER_USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    private AppStreamRegistryAdapter appStreamRegistryAdapter;

    @BeforeEach
    void setUp() {
        appStreamRegistryAdapter = new AppStreamRegistryAdapter();
    }

    @Test
    @DisplayName("같은 유저가 다시 연결하면 밀려난 이전 연결을 돌려준다")
    void returnsSupersededConnectionOnReconnect() {
        // given -> 앱 재시작처럼 옛 연결이 끊긴 줄 모르고 남아 있는 상황
        AppStreamConnection first = connection("conn-1");
        AppStreamConnection second = connection("conn-2");
        appStreamRegistryAdapter.register(USER_ID, first);

        // when
        Optional<AppStreamConnection> superseded = appStreamRegistryAdapter.register(USER_ID, second);

        // then
        assertThat(superseded).containsSame(first);
        assertThat(appStreamRegistryAdapter.find(USER_ID)).containsSame(second);
    }

    @Test
    @DisplayName("같은 연결이 다시 등록되면 밀어낼 것이 없다")
    void returnsEmptyWhenSameConnectionRegistersAgain() {
        // given
        AppStreamConnection connection = connection("conn-1");
        appStreamRegistryAdapter.register(USER_ID, connection);

        // when
        Optional<AppStreamConnection> superseded = appStreamRegistryAdapter.register(USER_ID, connection);

        // then
        assertThat(superseded).isEmpty();
    }

    @Test
    @DisplayName("다른 유저의 연결은 서로 밀어내지 않는다")
    void keepsConnectionsOfDifferentUsers() {
        // given
        AppStreamConnection mine = connection("conn-1");
        AppStreamConnection others = connection("conn-2");
        appStreamRegistryAdapter.register(USER_ID, mine);

        // when
        Optional<AppStreamConnection> superseded = appStreamRegistryAdapter.register(OTHER_USER_ID, others);

        // then
        assertThat(superseded).isEmpty();
        assertThat(appStreamRegistryAdapter.find(USER_ID)).containsSame(mine);
        assertThat(appStreamRegistryAdapter.find(OTHER_USER_ID)).containsSame(others);
    }

    @Test
    @DisplayName("새 연결이 자리를 가져간 뒤에는 옛 연결이 그 자리를 지우지 못한다")
    void keepsNewConnectionWhenSupersededOneIsRemovedLate() {
        // given -> 재연결 직후 옛 연결의 종료 콜백이 뒤늦게 도는 상황
        AppStreamConnection first = connection("conn-1");
        AppStreamConnection second = connection("conn-2");
        appStreamRegistryAdapter.register(USER_ID, first);
        appStreamRegistryAdapter.register(USER_ID, second);

        // when
        boolean removed = appStreamRegistryAdapter.remove(USER_ID, first);

        // then
        assertThat(removed).isFalse();
        assertThat(appStreamRegistryAdapter.find(USER_ID)).containsSame(second);
    }

    @Test
    @DisplayName("등록된 그 연결을 넘기면 지워진다")
    void removesRegisteredConnection() {
        // given
        AppStreamConnection connection = connection("conn-1");
        appStreamRegistryAdapter.register(USER_ID, connection);

        // when
        boolean removed = appStreamRegistryAdapter.remove(USER_ID, connection);

        // then
        assertThat(removed).isTrue();
        assertThat(appStreamRegistryAdapter.find(USER_ID)).isEmpty();
    }

    // 이 테스트는 연결 수명만 다룬다 — 식별자 말고는 연결의 동작을 쓰지 않는다
    private static AppStreamConnection connection(String id) {
        AppStreamConnection connection = mock(AppStreamConnection.class);
        lenient().when(connection.id()).thenReturn(id);
        return connection;
    }
}
