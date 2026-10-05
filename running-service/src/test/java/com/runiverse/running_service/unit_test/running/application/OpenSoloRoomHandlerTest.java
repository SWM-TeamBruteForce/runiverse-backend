package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomHandler;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomResult;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("솔로 러닝 시작 단위 테스트")
public class OpenSoloRoomHandlerTest {

    private static final int AVG_PACE = 330;          // 5분 30초/km
    private static final long PLAYER_ID = 42L;
    private static final long ROOM_ID = 125L;

    @Mock
    private ExistsActiveRunningPlayerPort existsActiveRunningPlayerPort;

    @Mock
    private LoadUserAvgPacePort loadUserAvgPacePort;

    @Mock
    private CreateRunningPlayerPort createRunningPlayerPort;

    @Mock
    private CreateRunningRoomPort createRunningRoomPort;

    @InjectMocks
    private OpenSoloRoomHandler handler;

    // 어댑터가 ID를 채워 돌려주는 상황을 흉내 낸다
    private static RunningPlayer savedPlayer(UUID userId) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(userId)
                .avgPace(AVG_PACE)
                .targetDistance(500_000)
                .startAt(LocalDateTime.now())
                .build();
    }

    private static RunningRoom savedRoom() {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.SOLO)
                .startAt(LocalDateTime.now())
                .avgPace(AVG_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(1)
                .build();
    }

    @Test
    @DisplayName("솔로 러닝을 시작하면 방 식별자를 돌려준다")
    void startSoloRunningSuccess() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(existsActiveRunningPlayerPort.existsActive(new UserId(userId))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId)))
                .willReturn(Optional.of(new Pace(AVG_PACE)));
        given(createRunningPlayerPort.create(any())).willReturn(savedPlayer(userId));
        given(createRunningRoomPort.create(any())).willReturn(savedRoom());

        // when
        OpenSoloRoomResult result = handler.handle(new OpenSoloRoomCommand(userId));

        // then -> 클라이언트는 이 ID로 WebSocket에 접속한다
        assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
        assertThat(result.startAt()).isNotNull();
    }

    @Test
    @DisplayName("신청은 목표 거리 없이 온보딩 페이스로 만들어진다")
    void createsPlayerFromOnboardingPace() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(existsActiveRunningPlayerPort.existsActive(new UserId(userId))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId)))
                .willReturn(Optional.of(new Pace(AVG_PACE)));
        given(createRunningPlayerPort.create(any())).willReturn(savedPlayer(userId));
        given(createRunningRoomPort.create(any())).willReturn(savedRoom());

        // when
        handler.handle(new OpenSoloRoomCommand(userId));

        // then -> 페이스는 입력받지 않고 서버가 세팅하고,
        //         목표 거리는 유저가 끝낼 때까지라 도달 불가능한 상한이 들어간다
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        org.mockito.Mockito.verify(createRunningPlayerPort).create(captor.capture());
        RunningPlayer requested = captor.getValue();

        assertThat(requested.getUserId().value()).isEqualTo(userId);
        assertThat(requested.getAvgPace().secondsPerKm()).isEqualTo(AVG_PACE);
        assertThat(requested.getTargetDistance().isUnlimited()).isTrue();
        assertThat(requested.getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
        assertThat(requested.isNew()).isTrue();
    }

    @Test
    @DisplayName("방은 모집 없이 MATCHED로 열리고 목표 거리를 갖지 않는다")
    void opensSoloRoomWithoutRecruiting() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(existsActiveRunningPlayerPort.existsActive(new UserId(userId))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId)))
                .willReturn(Optional.of(new Pace(AVG_PACE)));
        given(createRunningPlayerPort.create(any())).willReturn(savedPlayer(userId));
        given(createRunningRoomPort.create(any())).willReturn(savedRoom());

        // when
        handler.handle(new OpenSoloRoomCommand(userId));

        // then
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        org.mockito.Mockito.verify(createRunningRoomPort).create(captor.capture());
        RunningRoom opened = captor.getValue();

        assertThat(opened.getType()).isEqualTo(RunningRoomType.SOLO);
        // STARTED·RUNNING 전이는 WS 채널 입장 뒤 RUNNING_START가 맡는다 — 매칭과 같은 경로다
        assertThat(opened.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
        assertThat(opened.getCloseAt()).isEmpty();          // 방금 열린 방은 닫히지 않았다
        assertThat(opened.getTargetDistance()).isEmpty();   // 방 쪽은 nullable — null이 "목표 없음"의 정본
        assertThat(opened.getPlayerCount().current()).isEqualTo(1);
        assertThat(opened.getPlayerCount().max()).isEqualTo(1);
    }

    @Test
    @DisplayName("방의 세션은 개시한 유저와 저장된 신청 식별자를 참조한다")
    void roomSessionPointsAtSavedPlayer() {
        // given -> 신청을 먼저 저장해야 방이 참조할 ID가 생긴다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(existsActiveRunningPlayerPort.existsActive(new UserId(userId))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId)))
                .willReturn(Optional.of(new Pace(AVG_PACE)));
        given(createRunningPlayerPort.create(any())).willReturn(savedPlayer(userId));
        given(createRunningRoomPort.create(any())).willReturn(savedRoom());

        // when
        handler.handle(new OpenSoloRoomCommand(userId));

        // then
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        org.mockito.Mockito.verify(createRunningRoomPort).create(captor.capture());

        assertThat(captor.getValue().getSessions()).hasSize(1);
        RoomSession session = captor.getValue().getSessions().getFirst();
        // 세션의 키는 유저다 — 신청은 지금 이 방에 들어와 있는 것을 가리키는 값이다
        assertThat(session.isSameUser(new UserId(userId))).isTrue();
        assertThat(session.getRunningPlayerId()).isEqualTo(new RunningPlayerId(PLAYER_ID));
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    @DisplayName("진행 중인 신청이 있으면 새로 시작하지 못한다")
    void rejectsWhenAlreadyRunning() {
        // given -> "한 플레이어 = 최대 한 방"은 DB가 강제하지 않아 앱이 막는다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(existsActiveRunningPlayerPort.existsActive(new UserId(userId))).willReturn(true);

        // when & then
        assertThatThrownBy(() -> handler.handle(new OpenSoloRoomCommand(userId)))
                .isInstanceOf(AlreadyRunningException.class);

        // 중복 검사에서 걸리면 페이스 조회도 저장도 하지 않는다
        verifyNoInteractions(loadUserAvgPacePort, createRunningPlayerPort, createRunningRoomPort);
    }

    @Test
    @DisplayName("온보딩을 하지 않았으면 러닝을 시작하지 못한다")
    void rejectsWhenOnboardingNotCompleted() {
        // given -> 평균 페이스 출처가 user_onboardings라 row가 없으면 시작할 수 없다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(existsActiveRunningPlayerPort.existsActive(new UserId(userId))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new OpenSoloRoomCommand(userId)))
                .isInstanceOf(OnboardingNotCompletedException.class);

        // 아무것도 저장되지 않아야 한다 — 신청만 남으면 유저가 영영 다시 못 뛴다
        verifyNoInteractions(createRunningPlayerPort, createRunningRoomPort);
    }
}
