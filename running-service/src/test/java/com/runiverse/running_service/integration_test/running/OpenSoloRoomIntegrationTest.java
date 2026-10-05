package com.runiverse.running_service.integration_test.running;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomHandler;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomResult;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("솔로 러닝 시작 통합 테스트")
public class OpenSoloRoomIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String NICKNAME = "러너킴";
    private static final int AVG_PACE = 330;   // 5분 30초/km

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private OpenSoloRoomHandler handler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        handler = new OpenSoloRoomHandler(
                runningStore,     // ExistsActiveRunningPlayerPort
                onboardingStore,  // LoadUserAvgPacePort — 페이스 출처가 user_onboardings다
                runningStore,     // CreateRunningPlayerPort
                runningStore      // CreateRunningRoomPort
        );
    }

    private UUID signUp(String email) {
        return signUpHandler.handle(
                new SignUpCommand(issueVerificationTicket(email), PASSWORD)).userId();
    }

    private UUID onboardedUser() {
        UUID userId = signUp(EMAIL);
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                userId, NICKNAME, "MALE", LocalDate.of(1998, 5, 20),
                AVG_PACE, new BigDecimal("70.0"), new BigDecimal("175.0")));
        return userId;
    }

    @Test
    @DisplayName("온보딩을 마친 유저는 솔로 러닝을 시작하고 방 식별자를 받는다")
    void startSoloRunningSuccess() {
        // given
        UUID userId = onboardedUser();

        // when
        OpenSoloRoomResult result =
                handler.handle(new OpenSoloRoomCommand(userId));

        // then
        assertThat(result.runningRoomId()).isNotNull();
        assertThat(result.startAt()).isNotNull();
        assertThat(runningStore.findRoom(result.runningRoomId())).isPresent();
    }

    @Test
    @DisplayName("한 번 호출하면 신청·방·세션이 함께 만들어진다")
    void createsPlayerRoomAndSessionTogether() {
        // given -> 솔로도 매칭과 같이 방·플레이어 row를 만든다
        UUID userId = onboardedUser();

        // when
        OpenSoloRoomResult result =
                handler.handle(new OpenSoloRoomCommand(userId));

        // then
        assertThat(runningStore.playerCount()).isEqualTo(1);
        assertThat(runningStore.roomCount()).isEqualTo(1);

        RunningRoom room = runningStore.findRoom(result.runningRoomId()).orElseThrow();
        assertThat(room.getSessions()).hasSize(1);

        RunningPlayerId playerId = room.getSessions().get(0).getRunningPlayerId();
        assertThat(runningStore.findPlayer(playerId.value())).isPresent();
    }

    @Test
    @DisplayName("솔로 방은 모집 없이 MATCHED로 열리고 목표 거리를 갖지 않는다")
    void soloRoomOpensMatched() {
        // given
        UUID userId = onboardedUser();

        // when
        OpenSoloRoomResult result =
                handler.handle(new OpenSoloRoomCommand(userId));

        // then
        RunningRoom room = runningStore.findRoom(result.runningRoomId()).orElseThrow();
        assertThat(room.getType()).isEqualTo(RunningRoomType.SOLO);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
        assertThat(room.getCloseAt()).isEmpty();
        assertThat(room.getTargetDistance()).isEmpty();
        assertThat(room.getPlayerCount().max()).isEqualTo(1);
    }

    @Test
    @DisplayName("신청은 온보딩 페이스를 그대로 쓰고 목표 거리는 무제한이다")
    void playerUsesOnboardingPace() {
        // given -> 페이스는 입력받지 않고 서버가 온보딩 값에서 세팅한다
        UUID userId = onboardedUser();

        // when
        OpenSoloRoomResult result =
                handler.handle(new OpenSoloRoomCommand(userId));

        // then
        RunningRoom room = runningStore.findRoom(result.runningRoomId()).orElseThrow();
        RunningPlayerId playerId = room.getSessions().get(0).getRunningPlayerId();
        RunningPlayer player = runningStore.findPlayer(playerId.value()).orElseThrow();

        assertThat(player.getAvgPace().secondsPerKm()).isEqualTo(AVG_PACE);
        assertThat(player.getTargetDistance().isUnlimited()).isTrue();
        assertThat(player.getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
        assertThat(player.isActive()).isTrue();
    }

    @Test
    @DisplayName("진행 중인 러닝이 있으면 다시 시작하지 못한다")
    void cannotStartTwice() {
        // given -> 첫 러닝이 아직 끝나지 않았다(deleted_at IS NULL)
        UUID userId = onboardedUser();
        handler.handle(new OpenSoloRoomCommand(userId));

        // when & then
        assertThatThrownBy(() ->
                handler.handle(new OpenSoloRoomCommand(userId)))
                .isInstanceOf(AlreadyRunningException.class);

        // 두 번째 호출은 아무것도 남기지 않는다
        assertThat(runningStore.playerCount()).isEqualTo(1);
        assertThat(runningStore.roomCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("온보딩을 하지 않은 유저는 솔로 러닝을 시작하지 못한다")
    void cannotStartWithoutOnboarding() {
        // given -> 가입만 하고 온보딩은 안 한 유저
        UUID userId = signUp(EMAIL);

        // when & then -> 평균 페이스 출처가 user_onboardings라 시작할 수 없다
        assertThatThrownBy(() ->
                handler.handle(new OpenSoloRoomCommand(userId)))
                .isInstanceOf(OnboardingNotCompletedException.class);

        assertThat(runningStore.playerCount()).isZero();
        assertThat(runningStore.roomCount()).isZero();
    }

    @Test
    @DisplayName("다른 유저가 러닝 중이어도 내 러닝은 시작할 수 있다")
    void otherUsersRunningDoesNotBlock() {
        // given -> 중복 검사는 유저 단위다
        UUID other = onboardedUser();
        handler.handle(new OpenSoloRoomCommand(other));

        UUID me = signUp("other@runiverse.com");
        completeOnboardingHandler.handle(new CompleteOnboardingCommand(
                me, "완두콩", "FEMALE", LocalDate.of(2000, 1, 1),
                400, new BigDecimal("55.0"), new BigDecimal("165.0")));

        // when
        OpenSoloRoomResult result =
                handler.handle(new OpenSoloRoomCommand(me));

        // then
        assertThat(result.runningRoomId()).isNotNull();
        assertThat(runningStore.roomCount()).isEqualTo(2);
    }
}
