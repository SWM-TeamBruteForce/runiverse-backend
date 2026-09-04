package com.runiverse.running_service.unit_test.infrastructure.sse;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("매칭 스트림 레지스트리 단위 테스트")
class MatchStreamRegistryAdapterTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    private MatchStreamRegistryAdapter matchStreamRegistryAdapter;

    @BeforeEach
    void setUp() {
        matchStreamRegistryAdapter = new MatchStreamRegistryAdapter();
    }

    @Test
    @DisplayName("같은 유저가 다시 연결하면 밀려난 이전 연결을 돌려준다")
    void returnsSupersededConnectionOnReconnect() {
        // given -> 앱 재시작처럼 옛 연결이 끊긴 줄 모르고 남아 있는 상황
        FakeConnection first = new FakeConnection("conn-1");
        FakeConnection second = new FakeConnection("conn-2");
        matchStreamRegistryAdapter.register(USER_ID, first);

        // when
        Optional<MatchStreamConnection> superseded =
                matchStreamRegistryAdapter.register(USER_ID, second);

        // then
        assertThat(superseded).containsSame(first);
    }

    @Test
    @DisplayName("같은 연결이 다시 등록되면 밀어낼 것이 없다")
    void returnsEmptyWhenSameConnectionRegistersAgain() {
        // given -> 같은 소켓의 재등록은 밀어낼 대상이 아니다
        FakeConnection connection = new FakeConnection("conn-1");
        matchStreamRegistryAdapter.register(USER_ID, connection);

        // when
        Optional<MatchStreamConnection> superseded =
                matchStreamRegistryAdapter.register(USER_ID, connection);

        // then
        assertThat(superseded).isEmpty();
    }

    @Test
    @DisplayName("새 연결이 자리를 가져간 뒤에는 옛 연결이 그 자리를 지우지 못한다")
    void keepsNewConnectionWhenSupersededOneIsRemovedLate() {
        // given -> 재연결 직후 옛 연결의 종료 콜백이 뒤늦게 도는 상황
        FakeConnection first = new FakeConnection("conn-1");
        FakeConnection second = new FakeConnection("conn-2");
        matchStreamRegistryAdapter.register(USER_ID, first);
        matchStreamRegistryAdapter.register(USER_ID, second);

        // when
        boolean removed = matchStreamRegistryAdapter.remove(USER_ID, first);

        // then -> 새 연결은 그대로 남아 다음 등록에서 밀려난 쪽으로 돌아온다
        assertThat(removed).isFalse();
        assertThat(matchStreamRegistryAdapter.register(USER_ID, new FakeConnection("conn-3")))
                .containsSame(second);
    }

    @Test
    @DisplayName("등록된 그 연결을 넘기면 지워진다")
    void removesRegisteredConnection() {
        // given
        FakeConnection connection = new FakeConnection("conn-1");
        matchStreamRegistryAdapter.register(USER_ID, connection);

        // when
        boolean removed = matchStreamRegistryAdapter.remove(USER_ID, connection);

        // then -> 자리가 비었으므로 다음 등록은 아무도 밀어내지 않는다
        assertThat(removed).isTrue();
        assertThat(matchStreamRegistryAdapter.register(USER_ID, new FakeConnection("conn-2")))
                .isEmpty();
    }

    private static final class FakeConnection implements MatchStreamConnection {

        private final String id;

        private FakeConnection(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void keepAlive() {
        }

        @Override
        public void closeSuperseded() {
        }
    }
}
