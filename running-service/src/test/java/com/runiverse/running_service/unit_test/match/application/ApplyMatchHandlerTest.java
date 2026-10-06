package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchHandler;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchResult;
import com.runiverse.running_service.application.match.command.apply.MatchRoomAssigner;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.exception.MatchAlreadyInProgressException;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.application.match.exception.MatchSlotClosedException;
import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.MatchCooldownPort;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 신청 단위 테스트")
class ApplyMatchHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long PLAYER_ID = 7L;
    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;                      // 6분/km
    private static final int TARGET_DISTANCE = 5_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(15);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final int PACE_TIE_TOLERANCE = 10;
    // 이 테스트가 다루는 흐름은 아니지만 프로퍼티가 요구한다
    private static final Duration COOLDOWN = Duration.ofMinutes(20);
    // 조립 결과는 이 테스트의 주제가 아니다 — 발행 여부만 본다
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(2).minus(CLOSE_OFFSET),
            TARGET_DISTANCE, AVG_PACE, List.of());

    @Mock
    private MatchCooldownPort matchCooldownPort;

    @Mock
    private ExistsActiveApplicationPort existsActiveApplicationPort;

    @Mock
    private LoadUserAvgPacePort loadUserAvgPacePort;

    @Mock
    private CreateMatchApplicationPort createMatchApplicationPort;

    @Mock
    private MatchRoomAssigner matchRoomAssigner;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApplyMatchHandler applyMatchHandler;

    @BeforeEach
    void setUp() {
        applyMatchHandler = new ApplyMatchHandler(
                matchCooldownPort, existsActiveApplicationPort, loadUserAvgPacePort,
                createMatchApplicationPort,
                matchRoomAssigner, new MatchProperties(CLOSE_OFFSET, READY_OFFSET, FORCE_FINISH_OFFSET,
                        PACE_TIE_TOLERANCE, COOLDOWN),
                roomInfoAssembler, eventPublisher);
    }

    @Test
    @DisplayName("신청이 접수되면 배정된 방 ID를 돌려준다")
    void returnsAssignedRoomId() {
        // given
        givenApplicable();

        // when
        ApplyMatchResult result = applyMatchHandler.handle(command(slotAfter(Duration.ofHours(2))));

        // then -> 방 정보·참가자·마감 시각은 스트림이 나른다. 여기선 ID만 준다
        assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("페이스는 입력받지 않고 온보딩 값을 복사한다")
    void copiesAvgPaceFromOnboarding() {
        // given -> 매칭 조건에 페이스 항목이 없다(api-spec 5-A)
        givenApplicable();
        LocalDateTime startAt = slotAfter(Duration.ofHours(2));

        // when
        applyMatchHandler.handle(command(startAt));

        // then
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        verify(createMatchApplicationPort).create(captor.capture());
        RunningPlayer created = captor.getValue();
        assertThat(created.getAvgPace().secondsPerKm()).isEqualTo(AVG_PACE);
        assertThat(created.getTargetDistance().meters()).isEqualTo(TARGET_DISTANCE);
        assertThat(created.getStartAt()).isEqualTo(startAt);
    }

    @Test
    @DisplayName("신청을 먼저 저장해 배정에 넘길 식별자를 확보한다")
    void savesApplicationBeforeAssigning() {
        // given -> 방의 세션이 이 ID를 참조하므로 배정 전에 채워져 있어야 한다
        givenApplicable();

        // when
        applyMatchHandler.handle(command(slotAfter(Duration.ofHours(2))));

        // then
        verify(matchRoomAssigner).assign(
                eq(new UserId(USER_ID)), eq(new RunningPlayerId(PLAYER_ID)),
                any(Pace.class), any(LocalDateTime.class), eq(TARGET_DISTANCE));
    }

    @Test
    @DisplayName("모집이 마감된 슬롯은 신청하지 못한다")
    void rejectsClosedSlot() {
        // given -> 시작까지 5분 남았지만 마감(start_at - 15분)은 10분 전에 지났다.
        //          허용하면 방이 즉시 확정돼 아무도 못 만나고 혼자 뛰게 된다
        ApplyMatchCommand command = command(slotAfter(Duration.ofMinutes(5)));

        // when & then
        assertThatThrownBy(() -> applyMatchHandler.handle(command))
                .isInstanceOf(MatchSlotClosedException.class);
        // DB를 안 타는 검사라 제일 먼저 본다 — 뒤 조회가 아예 나가지 않아야 한다
        verifyNoInteractions(existsActiveApplicationPort, loadUserAvgPacePort,
                createMatchApplicationPort, matchRoomAssigner);
    }

    @Test
    @DisplayName("마감 시각 정각도 이미 마감으로 본다")
    void rejectsExactlyAtCloseTime() {
        // given -> 그 시점에 인원과 무관하게 확정이 일어난다(feature-spec 확정 판정)
        LocalDateTime startAt = LocalDateTime.now().plus(CLOSE_OFFSET);

        // when & then
        assertThatThrownBy(() -> applyMatchHandler.handle(command(startAt)))
                .isInstanceOf(MatchSlotClosedException.class);
    }

    @Test
    @DisplayName("제재 이탈로 쿨다운 중이면 신청하지 못한다")
    void rejectsWhileCoolingDown() {
        // given -> 근거는 DB의 status에 남고, "지금 막혀 있나"는 Redis 키가 답한다
        LocalDateTime cooldownUntil = LocalDateTime.now().plusMinutes(12);
        given(matchCooldownPort.until(new UserId(USER_ID)))
                .willReturn(Optional.of(cooldownUntil));
        ApplyMatchCommand command = command(slotAfter(Duration.ofHours(2)));

        // when & then -> 해제 시각을 함께 실어 클라가 "언제부터 되나"를 그릴 수 있게 한다
        assertThatThrownBy(() -> applyMatchHandler.handle(command))
                .isInstanceOf(MatchCooldownException.class)
                .extracting(e -> ((MatchCooldownException) e).getCooldownUntil())
                .isEqualTo(cooldownUntil);
        // Redis 키 하나로 끝나야 한다 — DB는 건드리지 않는다
        verifyNoInteractions(existsActiveApplicationPort, loadUserAvgPacePort,
                createMatchApplicationPort, matchRoomAssigner);
    }

    @Test
    @DisplayName("활성 신청이 있으면 다시 신청하지 못한다")
    void rejectsWhenAlreadyApplied() {
        // given -> deleted_at만 보므로 대기·확정뿐 아니라 러닝 중도 여기서 막힌다
        given(matchCooldownPort.until(new UserId(USER_ID))).willReturn(Optional.empty());
        given(existsActiveApplicationPort.existsActive(new UserId(USER_ID))).willReturn(true);
        ApplyMatchCommand command = command(slotAfter(Duration.ofHours(2)));

        // when & then
        assertThatThrownBy(() -> applyMatchHandler.handle(command))
                .isInstanceOf(MatchAlreadyInProgressException.class);
        verifyNoInteractions(createMatchApplicationPort, matchRoomAssigner);
    }

    @Test
    @DisplayName("온보딩을 마치지 않으면 신청하지 못한다")
    void rejectsWhenOnboardingNotCompleted() {
        // given -> 온보딩 완료 = user_onboardings row 존재라, 비어 있으면 곧 미완료다
        given(existsActiveApplicationPort.existsActive(new UserId(USER_ID))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(USER_ID))).willReturn(Optional.empty());
        ApplyMatchCommand command = command(slotAfter(Duration.ofHours(2)));

        // when & then -> 페이스를 못 구하면 방에 붙일 기준이 없다
        assertThatThrownBy(() -> applyMatchHandler.handle(command))
                .isInstanceOf(OnboardingNotCompletedException.class);
        verifyNoInteractions(createMatchApplicationPort, matchRoomAssigner);
    }

    private void givenApplicable() {
        given(matchCooldownPort.until(new UserId(USER_ID))).willReturn(Optional.empty());
        given(existsActiveApplicationPort.existsActive(new UserId(USER_ID))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(USER_ID)))
                .willReturn(Optional.of(new Pace(AVG_PACE)));
        given(createMatchApplicationPort.create(any())).willReturn(savedApplication());
        given(matchRoomAssigner.assign(any(), any(), any(), any(), anyInt()))
                .willReturn(assignedRoom());
        given(roomInfoAssembler.assemble(any())).willReturn(ROOM_INFO);
    }

    // 배정된 방 — 핸들러는 이걸 조립해 스트림에 실을 이벤트를 만든다
    private static RunningRoom assignedRoom() {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(RunningRoomStatus.MATCHING)
                .startAt(LocalDateTime.now().plusHours(2))
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .sessions(List.of(new SessionDraft(
                        new UserId(USER_ID), new RunningPlayerId(PLAYER_ID), 0, true)))
                .build();
    }

    // 마감(start_at - 15분)이 아직 안 지난 슬롯을 만든다
    private static LocalDateTime slotAfter(Duration untilStart) {
        return LocalDateTime.now().plus(untilStart);
    }

    private static ApplyMatchCommand command(LocalDateTime startAt) {
        return new ApplyMatchCommand(USER_ID, startAt, TARGET_DISTANCE);
    }

    // 저장 후 ID가 채워진 신청 — 배정이 이 ID를 세션에 꽂는다
    private static RunningPlayer savedApplication() {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(USER_ID)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(LocalDateTime.now().plusHours(2))
                .build();
    }
}
