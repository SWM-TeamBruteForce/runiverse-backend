package com.runiverse.running_service.unit_test.infrastructure.websocket;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.websocket.RunningRoomMemberRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("러닝 방 명부 단위 테스트")
class RunningRoomMemberRegistryTest {

    private static final long ROOM_ID = 125L;

    private RunningRoomMemberRegistry registry;
    private UserId first;
    private UserId second;

    @BeforeEach
    void setUp() {
        registry = new RunningRoomMemberRegistry();
        first = new UserId(UuidCreator.getTimeOrderedEpoch());
        second = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    @DisplayName("첫 참가자 입장에만 구독할 방 번호를 돌려준다")
    void returnsRoomOnlyOnFirstJoin() {
        // when & then
        assertThat(registry.join(first, ROOM_ID)).contains(ROOM_ID);
        assertThat(registry.join(second, ROOM_ID)).isEmpty();
        assertThat(registry.usersIn(ROOM_ID)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("마지막 참가자 퇴장에만 구독을 끊을 방 번호를 돌려준다")
    void returnsRoomOnlyOnLastLeave() {
        // given
        registry.join(first, ROOM_ID);
        registry.join(second, ROOM_ID);

        // when & then
        assertThat(registry.leave(first)).isEmpty();
        assertThat(registry.leave(second)).contains(ROOM_ID);
        assertThat(registry.usersIn(ROOM_ID)).isEmpty();
    }

    @Test
    @DisplayName("같은 참가자의 재입장은 첫 참가자가 아니다")
    void rejoinIsNotFirst() {
        // given
        registry.join(first, ROOM_ID);

        // when & then -> 재연결이 구독을 다시 켜게 하면 중복 구독이 된다
        assertThat(registry.join(first, ROOM_ID)).isEmpty();
        assertThat(registry.usersIn(ROOM_ID)).containsExactly(first);
    }
}
