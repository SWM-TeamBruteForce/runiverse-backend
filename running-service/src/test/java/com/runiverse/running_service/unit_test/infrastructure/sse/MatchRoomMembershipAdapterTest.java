package com.runiverse.running_service.unit_test.infrastructure.sse;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.exception.MatchStreamUnavailableException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.match.MatchRoomSubscriber;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMembershipAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 이 인스턴스의 방 명부와 방 채널 구독이 어긋나지 않는지 본다(architecture.md 「다중 인스턴스」).
// 같은 인스턴스에서 새 연결로 갈아타면 옛 연결의 종료 처리는 새 연결 보호 가드에 걸려 leave()를 건너뛴다 —
// 그래서 옛 방 정리는 새 방에 들어가는 join()이 맡아야 한다
@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 방 명부·구독 어댑터 단위 테스트")
class MatchRoomMembershipAdapterTest {

    private static final long OLD_ROOM = 125L;
    private static final long NEW_ROOM = 130L;

    // 구독은 Redis 컨테이너를 건드리는 일이라 가짜로 둔다
    @Mock
    private MatchRoomSubscriber matchRoomSubscriber;

    private MatchRoomMemberRegistry registry;
    private MatchRoomMembershipAdapter adapter;
    private UserId chulsu;
    private UserId younghee;

    @BeforeEach
    void setUp() {
        // 명부는 상태만 드는 POJO라 실제 구현을 쓴다 — 두 표(사람→방, 방→사람)가 어긋나는지 직접 봐야 한다
        registry = new MatchRoomMemberRegistry();
        adapter = new MatchRoomMembershipAdapter(registry, matchRoomSubscriber);
        chulsu = new UserId(UuidCreator.getTimeOrderedEpoch());
        younghee = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Nested
    @DisplayName("들어오기·나가기 테스트")
    class JoinLeaveTest {

        @Test
        @DisplayName("방의 첫 참가자가 들어오면 방 채널을 구독하고 명부에 올린다")
        void subscribesOnFirstJoin() {
            // when
            adapter.join(chulsu, OLD_ROOM);

            // then
            verify(matchRoomSubscriber).subscribe(OLD_ROOM);
            assertThat(registry.usersIn(OLD_ROOM)).containsExactly(chulsu);
        }

        @Test
        @DisplayName("두 번째 참가자는 구독을 다시 걸지 않는다")
        void secondJoinDoesNotResubscribe() {
            // when
            adapter.join(chulsu, OLD_ROOM);
            adapter.join(younghee, OLD_ROOM);

            // then
            verify(matchRoomSubscriber, times(1)).subscribe(OLD_ROOM);
            assertThat(registry.usersIn(OLD_ROOM)).containsExactlyInAnyOrder(chulsu, younghee);
        }

        @Test
        @DisplayName("마지막 참가자가 나가면 방 채널 구독을 끊는다")
        void unsubscribesWhenLastLeaves() {
            // given
            adapter.join(chulsu, OLD_ROOM);

            // when
            adapter.leave(chulsu);

            // then
            verify(matchRoomSubscriber).unsubscribe(OLD_ROOM);
            assertThat(registry.usersIn(OLD_ROOM)).isEmpty();
        }
    }

    @Nested
    @DisplayName("방 갈아타기 테스트")
    class SwitchRoomTest {

        @Test
        @DisplayName("다른 방에 들어가면 옛 방 명부에서 빠진다 — 옛 방 이벤트가 새 연결로 가지 않는다")
        void leavesOldRoomOnSwitch() {
            // given -> 철수와 영희가 옛 방에 있다. 철수의 옛 연결 종료 처리는 가드에 걸려 leave()가 돌지 않았다
            adapter.join(chulsu, OLD_ROOM);
            adapter.join(younghee, OLD_ROOM);

            // when -> 같은 인스턴스에서 새 연결로 새 방에 들어간다
            adapter.join(chulsu, NEW_ROOM);

            // then -> 한 사람이 두 방 명부에 동시에 있으면 안 된다
            assertThat(registry.usersIn(OLD_ROOM)).containsExactly(younghee);
            assertThat(registry.usersIn(NEW_ROOM)).containsExactly(chulsu);
        }

        @Test
        @DisplayName("옛 방의 마지막 참가자였으면 옛 방 구독을 끊고 새 방을 구독한다")
        void unsubscribesOldRoomWhenLastMemberSwitches() {
            // given
            adapter.join(chulsu, OLD_ROOM);

            // when
            adapter.join(chulsu, NEW_ROOM);

            // then -> 안 끊으면 이 인스턴스는 아무도 없는 방의 메시지를 계속 받는다
            verify(matchRoomSubscriber).unsubscribe(OLD_ROOM);
            verify(matchRoomSubscriber).subscribe(NEW_ROOM);
            assertThat(registry.usersIn(OLD_ROOM)).isEmpty();
        }

        @Test
        @DisplayName("옛 방에 다른 참가자가 남아 있으면 옛 방 구독은 그대로 둔다")
        void keepsOldRoomSubscriptionWhileOthersRemain() {
            // given
            adapter.join(chulsu, OLD_ROOM);
            adapter.join(younghee, OLD_ROOM);

            // when
            adapter.join(chulsu, NEW_ROOM);

            // then -> 영희는 계속 옛 방 이벤트를 받아야 한다
            verify(matchRoomSubscriber, never()).unsubscribe(OLD_ROOM);
        }

        @Test
        @DisplayName("갈아탄 뒤 나가면 새 방에서만 빠지고 옛 방에는 흔적이 남지 않는다")
        void leaveAfterSwitchCleansEverything() {
            // given
            adapter.join(chulsu, OLD_ROOM);
            adapter.join(chulsu, NEW_ROOM);

            // when -> 새 연결이 닫힌다
            adapter.leave(chulsu);

            // then
            assertThat(registry.usersIn(OLD_ROOM)).isEmpty();
            assertThat(registry.usersIn(NEW_ROOM)).isEmpty();
        }
    }

    @Nested
    @DisplayName("같은 방 재연결 테스트")
    class RejoinSameRoomTest {

        @Test
        @DisplayName("같은 방으로 다시 들어오면 구독을 끊었다 걸지 않고 명부도 그대로다")
        void sameRoomRejoinKeepsSubscription() {
            // given
            adapter.join(chulsu, OLD_ROOM);

            // when -> 앱 재시작처럼 같은 방으로 새 연결이 붙는다
            adapter.join(chulsu, OLD_ROOM);

            // then -> 끊었다 다시 걸면 그 틈의 이벤트를 놓친다
            verify(matchRoomSubscriber, never()).unsubscribe(OLD_ROOM);
            verify(matchRoomSubscriber, times(1)).subscribe(OLD_ROOM);
            assertThat(registry.usersIn(OLD_ROOM)).containsExactly(chulsu);
        }
    }

    @Nested
    @DisplayName("구독 실패 테스트")
    class SubscribeFailureTest {

        @Test
        @DisplayName("구독에 실패하면 명부 등록을 되돌리고 예외를 그대로 알린다")
        void rollsBackMembershipWhenSubscribeFails() {
            // given
            willThrow(new MatchStreamUnavailableException())
                    .given(matchRoomSubscriber).subscribe(OLD_ROOM);

            // when & then -> 구독 없이 명부만 남으면 재연결이 첫 참가자가 아니게 돼 구독을 영영 건너뛴다
            assertThatThrownBy(() -> adapter.join(chulsu, OLD_ROOM))
                    .isInstanceOf(MatchStreamUnavailableException.class);
            assertThat(registry.usersIn(OLD_ROOM)).isEmpty();
            assertThat(registry.roomOf(chulsu)).isEmpty();
            verify(matchRoomSubscriber).unsubscribe(OLD_ROOM);
        }

        @Test
        @DisplayName("구독 실패 뒤 같은 방으로 다시 들어오면 다시 첫 참가자가 되어 구독을 다시 시도한다")
        void retriesSubscriptionAfterRollback() {
            // given -> 첫 시도만 실패하는 일시 장애
            willThrow(new MatchStreamUnavailableException())
                    .willDoNothing()
                    .given(matchRoomSubscriber).subscribe(OLD_ROOM);
            assertThatThrownBy(() -> adapter.join(chulsu, OLD_ROOM))
                    .isInstanceOf(MatchStreamUnavailableException.class);

            // when -> 앱이 다시 연결한다
            adapter.join(chulsu, OLD_ROOM);

            // then
            verify(matchRoomSubscriber, times(2)).subscribe(OLD_ROOM);
            assertThat(registry.usersIn(OLD_ROOM)).containsExactly(chulsu);
        }
    }
}
