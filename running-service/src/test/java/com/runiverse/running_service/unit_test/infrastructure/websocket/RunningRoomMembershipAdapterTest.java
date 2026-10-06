package com.runiverse.running_service.unit_test.infrastructure.websocket;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.exception.RunningSessionUnavailableException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomSubscriber;
import com.runiverse.running_service.infrastructure.websocket.RunningRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.websocket.RunningRoomMembershipAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 방 명부·구독 어댑터 단위 테스트")
class RunningRoomMembershipAdapterTest {

    private static final long ROOM_ID = 125L;

    // 구독은 Redis 컨테이너를 건드리는 일이라 가짜로 둔다
    @Mock
    private RunningRoomSubscriber runningRoomSubscriber;

    private RunningRoomMemberRegistry registry;
    private RunningRoomMembershipAdapter adapter;
    private UserId userId;

    @BeforeEach
    void setUp() {
        // 명부는 상태만 드는 POJO라 실제 구현을 쓴다 — 실패 후 재시도가 진짜로 도는지 봐야 한다
        registry = new RunningRoomMemberRegistry();
        adapter = new RunningRoomMembershipAdapter(registry, runningRoomSubscriber);
        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    @DisplayName("첫 참가자가 들어오면 방 채널을 구독하고 명부에 올린다")
    void subscribesOnFirstJoin() {
        // when
        adapter.join(userId, ROOM_ID);

        // then
        verify(runningRoomSubscriber).subscribe(ROOM_ID);
        assertThat(registry.usersIn(ROOM_ID)).containsExactly(userId);
    }

    @Test
    @DisplayName("구독에 실패하면 명부 등록을 되돌리고 예외를 그대로 알린다")
    void rollsBackMembershipWhenSubscribeFails() {
        // given
        willThrow(new RunningSessionUnavailableException())
                .given(runningRoomSubscriber).subscribe(ROOM_ID);

        // when & then -> 구독 없이 명부만 남으면 재시도가 첫 참가자가 아니게 돼 구독을 영영 건너뛴다
        assertThatThrownBy(() -> adapter.join(userId, ROOM_ID))
                .isInstanceOf(RunningSessionUnavailableException.class);
        assertThat(registry.usersIn(ROOM_ID)).isEmpty();
        assertThat(registry.roomOf(userId)).isEmpty();
    }

    @Test
    @DisplayName("구독 실패 후 재시도는 다시 첫 참가자가 되어 구독을 다시 시도한다")
    void retriesSubscriptionAfterRollback() {
        // given -> 첫 시도만 실패하는 일시 장애
        willThrow(new RunningSessionUnavailableException())
                .willDoNothing()
                .given(runningRoomSubscriber).subscribe(ROOM_ID);
        assertThatThrownBy(() -> adapter.join(userId, ROOM_ID))
                .isInstanceOf(RunningSessionUnavailableException.class);

        // when
        adapter.join(userId, ROOM_ID);

        // then
        verify(runningRoomSubscriber, times(2)).subscribe(ROOM_ID);
        assertThat(registry.usersIn(ROOM_ID)).containsExactly(userId);
    }
}
