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
import static org.mockito.Mockito.never;
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
    @DisplayName("다른 방에 들어가면 옛 방 명부에서 빠지고, 옛 방의 마지막 참가자였으면 옛 방 구독을 끊는다")
    void leavesOldRoomOnSwitch() {
        // given -> 같은 인스턴스에서 옛 연결의 종료 처리가 새 연결 보호 가드에 걸려 leave()를 건너뛴 상태
        long newRoom = ROOM_ID + 1;
        adapter.join(userId, ROOM_ID);

        // when
        adapter.join(userId, newRoom);

        // then -> 한 사람이 두 방 명부에 남지 않는다(architecture.md 「다중 인스턴스」 1번)
        assertThat(registry.usersIn(ROOM_ID)).isEmpty();
        assertThat(registry.usersIn(newRoom)).containsExactly(userId);
        assertThat(registry.roomOf(userId)).contains(newRoom);
        verify(runningRoomSubscriber).unsubscribe(ROOM_ID);
        verify(runningRoomSubscriber).subscribe(newRoom);
    }

    @Test
    @DisplayName("옛 방에 다른 참가자가 남아 있으면 갈아타도 옛 방 구독은 그대로 둔다")
    void keepsOldRoomSubscriptionWhileOthersRemain() {
        // given
        UserId other = new UserId(UuidCreator.getTimeOrderedEpoch());
        adapter.join(userId, ROOM_ID);
        adapter.join(other, ROOM_ID);

        // when
        adapter.join(userId, ROOM_ID + 1);

        // then
        assertThat(registry.usersIn(ROOM_ID)).containsExactly(other);
        verify(runningRoomSubscriber, never()).unsubscribe(ROOM_ID);
    }

    @Test
    @DisplayName("같은 방으로 다시 들어오면 구독을 끊었다 걸지 않는다")
    void sameRoomRejoinKeepsSubscription() {
        // given
        adapter.join(userId, ROOM_ID);

        // when -> 재연결
        adapter.join(userId, ROOM_ID);

        // then -> 끊었다 다시 걸면 그 틈의 진행 통지를 놓친다
        verify(runningRoomSubscriber, never()).unsubscribe(ROOM_ID);
        verify(runningRoomSubscriber, times(1)).subscribe(ROOM_ID);
        assertThat(registry.usersIn(ROOM_ID)).containsExactly(userId);
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
