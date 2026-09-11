package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.combo.BroadcastRunningComboCommand;
import com.runiverse.running_service.application.running.command.combo.BroadcastRunningComboHandler;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomMembersPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPeer;
import com.runiverse.running_service.application.running.port.out.RunningComboRelation;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;
import com.runiverse.running_service.application.running.port.out.RunningConnection;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 방 전체의 관계 목록을 받는 사람 기준의 상대 목록으로 바꾸는 변환이 여기 있다.
// 같은 관계라도 양쪽이 받는 값의 부호가 반대라는 것이 핵심이다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 분배 단위 테스트")
class BroadcastRunningComboHandlerTest {

    private static final Long ROOM_ID = 42L;
    private static final UserId A =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1"));
    private static final UserId B =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000b2"));
    private static final UserId C =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000c3"));

    // B가 A보다 18m 앞서 있다는 뜻이다 — 통에는 second 기준 부호로 실려 온다
    private static final RunningComboRelation A_TO_B =
            new RunningComboRelation(A.value(), B.value(), 18, 12, 30);
    private static final RunningComboRelation B_TO_C =
            new RunningComboRelation(B.value(), C.value(), -7, 4, 4);

    @Mock
    private LoadRunningRoomMembersPort loadRunningRoomMembersPort;

    @Mock
    private RunningSessionPort runningSessionPort;

    @Mock
    private RunningConnection connection;

    private BroadcastRunningComboHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BroadcastRunningComboHandler(loadRunningRoomMembersPort, runningSessionPort);
    }

    @Test
    @DisplayName("관계의 first가 받으면 부호를 그대로 쓴다")
    void handle_keepsGapSignForFirst() {
        // given
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(A));
        given(runningSessionPort.find(A)).willReturn(Optional.of(connection));

        // when
        handler.handle(command(Set.of(A.value()), List.of(A_TO_B)));

        // then -> A가 볼 때 상대 B는 18m 앞이다
        List<RunningComboPeer> peers = capturePeers();
        assertThat(peers).hasSize(1);
        assertThat(peers.getFirst().userId()).isEqualTo(B.value());
        assertThat(peers.getFirst().gapMeters()).isEqualTo(18);
        assertThat(peers.getFirst().comboCount()).isEqualTo(12);
        assertThat(peers.getFirst().maxComboCount()).isEqualTo(30);
    }

    @Test
    @DisplayName("관계의 second가 받으면 부호를 뒤집는다")
    void handle_flipsGapSignForSecond() {
        // given -> 같은 관계를 반대쪽이 받는다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(B));
        given(runningSessionPort.find(B)).willReturn(Optional.of(connection));

        // when
        handler.handle(command(Set.of(B.value()), List.of(A_TO_B)));

        // then -> B가 볼 때 상대 A는 18m 뒤다
        List<RunningComboPeer> peers = capturePeers();
        assertThat(peers.getFirst().userId()).isEqualTo(A.value());
        assertThat(peers.getFirst().gapMeters()).isEqualTo(-18);
    }

    @Test
    @DisplayName("받는 사람이 낀 관계만 담는다")
    void handle_includesOnlyOwnRelations() {
        // given -> 통에는 방 전체의 관계가 실려 오지만 A는 B-C를 볼 이유가 없다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(A));
        given(runningSessionPort.find(A)).willReturn(Optional.of(connection));

        // when
        handler.handle(command(Set.of(A.value()), List.of(A_TO_B, B_TO_C)));

        // then
        List<RunningComboPeer> peers = capturePeers();
        assertThat(peers).hasSize(1);
        assertThat(peers.getFirst().userId()).isEqualTo(B.value());
    }

    @Test
    @DisplayName("보낸 사람과 무관한 관계라도 받는 사람이 끼면 담는다")
    void handle_includesRelationsWithOtherPeers() {
        // given -> B는 A와도 C와도 콤보다. A의 배치로 만든 통이어도 B-C가 빠지면
        // B 화면에서 C 콤보가 사라진다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(B));
        given(runningSessionPort.find(B)).willReturn(Optional.of(connection));

        // when
        handler.handle(command(Set.of(B.value()), List.of(A_TO_B, B_TO_C)));

        // then
        assertThat(capturePeers())
                .extracting(RunningComboPeer::userId)
                .containsExactlyInAnyOrder(A.value(), C.value());
    }

    @Test
    @DisplayName("겹치는 상대가 없으면 빈 목록을 보낸다")
    void handle_sendsEmptyPeersWhenNothingOverlaps() {
        // given -> 목록이 비는 것이 곧 "화면을 비우라"는 뜻이라 보내지 않으면 안 된다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(A));
        given(runningSessionPort.find(A)).willReturn(Optional.of(connection));

        // when
        handler.handle(command(Set.of(A.value()), List.of()));

        // then
        assertThat(capturePeers()).isEmpty();
    }

    @Test
    @DisplayName("이 인스턴스에 연결이 없는 수신자는 건너뛴다")
    void handle_skipsRecipientWithoutLocalConnection() {
        // given -> B는 다른 서버에 붙어 있다. 그쪽 인스턴스가 같은 메시지를 받아 처리한다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(A, B));
        given(runningSessionPort.find(A)).willReturn(Optional.of(connection));
        given(runningSessionPort.find(B)).willReturn(Optional.empty());

        // when
        handler.handle(command(Set.of(A.value(), B.value()), List.of(A_TO_B)));

        // then -> A에게만 한 번 나간다
        verify(connection).sendCombo(anyList());
    }

    @Test
    @DisplayName("이 방의 참가자가 아닌 수신자는 찾지도 않는다")
    void handle_ignoresRecipientOutsideRoom() {
        // given -> 통이 건너오는 사이 C가 러닝을 끝내고 다른 방에 붙었다면,
        // 세션은 찾아지지만 그 화면에 옛 방의 콤보를 밀어 넣으면 안 된다
        given(loadRunningRoomMembersPort.usersIn(ROOM_ID)).willReturn(Set.of(A));
        given(runningSessionPort.find(A)).willReturn(Optional.of(connection));

        // when
        handler.handle(command(Set.of(A.value(), C.value()), List.of(A_TO_B)));

        // then
        verify(runningSessionPort, never()).find(C);
    }

    private static BroadcastRunningComboCommand command(
            Set<UUID> recipients, List<RunningComboRelation> relations) {
        return new BroadcastRunningComboCommand(
                ROOM_ID, new RunningComboUpdate(recipients, relations));
    }

    @SuppressWarnings("unchecked")
    private List<RunningComboPeer> capturePeers() {
        ArgumentCaptor<List<RunningComboPeer>> captor = ArgumentCaptor.forClass(List.class);
        verify(connection).sendCombo(captor.capture());
        return captor.getValue();
    }
}
