package com.runiverse.running_service.integration_test.match;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomHandler;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomResult;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InviteStartAtOutOfRangeException;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomHandler;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import com.runiverse.running_service.integration_test.fake.InMemoryScheduledJobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@DisplayName("초대 방 개설 통합 테스트")
public class OpenInviteRoomIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String NICKNAME = "러너킴";
    private static final int AVG_PACE = 330;   // 5분 30초/km
    private static final int TARGET_DISTANCE = 7_000;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 26, 19, 0);
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final Duration REMINDER_OFFSET = Duration.ofMinutes(5);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), REMINDER_OFFSET, READY_OFFSET, FORCE_FINISH_OFFSET, 10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
    private static final InviteProperties INVITE_PROPERTIES = new InviteProperties(
            Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, 5);

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private InMemoryScheduledJobStore scheduledJobStore;
    private InviteStartPolicy inviteStartPolicy;
    private OpenInviteRoomHandler handler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        scheduledJobStore = new InMemoryScheduledJobStore();
        // 시각 범위만 고정한다 — 핸들러가 지금 시각을 읽어 23:49 이후에 돌면 고를 시각이 없다.
        // 범위 밖 거절은 내일 시각으로 실제 판정을 탄다
        inviteStartPolicy = spy(new InviteStartPolicy(INVITE_PROPERTIES, MATCH_PROPERTIES));
        doReturn(true).when(inviteStartPolicy).isSelectable(any(), any());
        handler = new OpenInviteRoomHandler(
                inviteStartPolicy,
                runningStore,     // ExistsActiveApplicationPort
                onboardingStore,  // LoadUserAvgPacePort — 페이스 출처가 user_onboardings다
                runningStore,     // CreateMatchApplicationPort
                runningStore,     // CreateMatchRoomPort
                new ScheduleJobHandler(scheduledJobStore, event -> {
                }),               // ScheduleJobPort
                MATCH_PROPERTIES
        );
    }

    private UUID onboardedUser(String email, String nickname) {
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, nickname, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, new BigDecimal("70.0"), new BigDecimal("175.0")));
        return userId;
    }

    private OpenInviteRoomResult open(UUID userId) {
        return handler.handle(new OpenInviteRoomCommand(userId, START, TARGET_DISTANCE));
    }

    private LocalDateTime executeAtOf(ScheduledJobType type, Long runningRoomId) {
        return scheduledJobStore.findBy(type, runningRoomId).orElseThrow().getExecuteAt();
    }

    @Test
    @DisplayName("한 번 호출하면 신청·초대 방·세션이 함께 만들어진다")
    void createsPlayerRoomAndSessionTogether() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        OpenInviteRoomResult result = open(userId);

        // then
        RunningRoom room = runningStore.findRoom(result.runningRoomId()).orElseThrow();
        assertThat(room.getType()).isEqualTo(RunningRoomType.INVITE);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(room.getStartAt()).isEqualTo(START);
        assertThat(room.getTargetDistance().orElseThrow().meters()).isEqualTo(TARGET_DISTANCE);
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        assertThat(room.getPlayerCount().max()).isEqualTo(4);
        assertThat(room.getSessions()).hasSize(1);

        RunningPlayer player = runningStore.findPlayer(
                room.getSessions().getFirst().getRunningPlayerId().value()).orElseThrow();
        assertThat(player.getAvgPace().secondsPerKm()).isEqualTo(AVG_PACE);
        assertThat(player.getStartAt()).isEqualTo(START);
        assertThat(player.isActive()).isTrue();
    }

    @Test
    @DisplayName("예약 네 가지가 저장되고 마감은 시작 1분 전이다")
    void storesFourScheduledJobs() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);

        // when
        Long roomId = open(userId).runningRoomId();

        // then
        assertThat(scheduledJobStore.jobCount()).isEqualTo(4);
        assertThat(executeAtOf(ScheduledJobType.MATCH_CLOSE, roomId)).isEqualTo(START.minus(CLOSE_OFFSET));
        assertThat(executeAtOf(ScheduledJobType.RUNNING_READY, roomId)).isEqualTo(START.minus(READY_OFFSET));
        assertThat(executeAtOf(ScheduledJobType.RUNNING_START, roomId)).isEqualTo(START);
        assertThat(executeAtOf(ScheduledJobType.RUNNING_FORCE_FINISH, roomId))
                .isEqualTo(START.plus(FORCE_FINISH_OFFSET));
    }

    @Test
    @DisplayName("초대 방에 있는 동안 다시 초대 방을 열지 못한다")
    void cannotOpenTwice() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        open(userId);

        // when & then
        assertThatThrownBy(() -> open(userId)).isInstanceOf(AlreadyRunningException.class);

        assertThat(runningStore.roomCount()).isEqualTo(1);
        assertThat(scheduledJobStore.jobCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("솔로 러닝 중이면 초대 방을 열지 못한다")
    void cannotOpenWhileSoloRunning() {
        // given -> 활성 신청은 방 종류와 무관하게 하나다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        new OpenSoloRoomHandler(runningStore, onboardingStore, runningStore, runningStore)
                .handle(new OpenSoloRoomCommand(userId));

        // when & then
        assertThatThrownBy(() -> open(userId)).isInstanceOf(AlreadyRunningException.class);
        assertThat(runningStore.roomCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("온보딩을 하지 않은 유저는 초대 방을 열지 못하고 아무것도 남지 않는다")
    void cannotOpenWithoutOnboarding() {
        // given
        UUID userId = signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(EMAIL), PASSWORD)).userId();

        // when & then
        assertThatThrownBy(() -> open(userId)).isInstanceOf(OnboardingNotCompletedException.class);

        assertThat(runningStore.playerCount()).isZero();
        assertThat(runningStore.roomCount()).isZero();
        assertThat(scheduledJobStore.jobCount()).isZero();
    }

    @Test
    @DisplayName("내일 시각은 실제 판정으로 거절되고 아무것도 남지 않는다")
    void tomorrowIsRejected() {
        // given -> 고정을 풀고 실제 판정을 탄다. 내일은 언제 돌아도 범위 밖이다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        doCallRealMethod().when(inviteStartPolicy).isSelectable(any(), any());
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atTime(7, 30);

        // when & then
        assertThatThrownBy(() -> handler.handle(new OpenInviteRoomCommand(userId, tomorrow, TARGET_DISTANCE)))
                .isInstanceOf(InviteStartAtOutOfRangeException.class);

        assertThat(runningStore.playerCount()).isZero();
        assertThat(runningStore.roomCount()).isZero();
    }
}
