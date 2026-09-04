package com.runiverse.running_service.unit_test.infrastructure.sse;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.sse.MatchStreamKeepAlive;
import com.runiverse.running_service.infrastructure.sse.MatchStreamRegistryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("매칭 스트림 keep-alive 단위 테스트")
class MatchStreamKeepAliveTest {

    private MatchStreamRegistryAdapter matchStreamRegistryAdapter;
    private MatchStreamKeepAlive matchStreamKeepAlive;

    @BeforeEach
    void setUp() {
        matchStreamRegistryAdapter = new MatchStreamRegistryAdapter();
        matchStreamKeepAlive = new MatchStreamKeepAlive(matchStreamRegistryAdapter);
    }

    @Test
    @DisplayName("등록된 모든 연결에 ping을 보낸다")
    void pingsEveryRegisteredConnection() {
        // given -> 서로 다른 유저의 연결이 이 인스턴스에 붙어 있는 상황
        FakeConnection first = register("conn-1");
        FakeConnection second = register("conn-2");

        // when
        matchStreamKeepAlive.pingAll();

        // then
        assertThat(first.keepAliveCount).isOne();
        assertThat(second.keepAliveCount).isOne();
    }

    @Test
    @DisplayName("한 연결이 실패해도 나머지 연결의 ping은 계속된다")
    void keepsPingingWhenOneConnectionFails() {
        // given -> 끊긴 단말 하나가 전송 중 예외를 던지는 상황
        FakeConnection first = register("conn-1");
        FakeConnection failing = register("conn-2");
        failing.failOnKeepAlive = true;
        FakeConnection third = register("conn-3");

        // when & then -> 순회가 중간에 끊기면 뒤 연결이 프록시 유휴 타임아웃에 걸린다
        assertThatCode(() -> matchStreamKeepAlive.pingAll()).doesNotThrowAnyException();
        assertThat(first.keepAliveCount).isOne();
        assertThat(failing.keepAliveCount).isOne();
        assertThat(third.keepAliveCount).isOne();
    }

    @Test
    @DisplayName("레지스트리에서 빠진 연결에는 ping을 보내지 않는다")
    void doesNotPingRemovedConnection() {
        // given -> 종료 콜백이 돌아 이미 정리된 연결
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        FakeConnection connection = new FakeConnection("conn-1");
        matchStreamRegistryAdapter.register(userId, connection);
        matchStreamRegistryAdapter.remove(userId, connection);

        // when
        matchStreamKeepAlive.pingAll();

        // then
        assertThat(connection.keepAliveCount).isZero();
    }

    private FakeConnection register(String id) {
        FakeConnection connection = new FakeConnection(id);
        matchStreamRegistryAdapter.register(new UserId(UuidCreator.getTimeOrderedEpoch()), connection);
        return connection;
    }

    private static final class FakeConnection implements MatchStreamConnection {

        private final String id;
        private int keepAliveCount;
        private boolean failOnKeepAlive;

        private FakeConnection(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void keepAlive() {
            keepAliveCount++;
            if (failOnKeepAlive) {
                throw new IllegalStateException("전송 실패");
            }
        }

        @Override
        public void closeSuperseded() {
        }
    }
}
