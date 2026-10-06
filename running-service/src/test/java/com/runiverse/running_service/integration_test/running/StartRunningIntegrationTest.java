package com.runiverse.running_service.integration_test.running;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomHandler;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningHandler;
import com.runiverse.running_service.application.running.command.start.StartRunningResult;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingHandler;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.integration_test.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("러닝 시작 통합 테스트")
public class StartRunningIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String EMAIL = "runner@runiverse.com";
    private static final String NICKNAME = "러너킴";
    private static final int AVG_PACE = 330;   // 5분 30초/km

    private SignUpHandler signUpHandler;
    private CompleteOnboardingHandler completeOnboardingHandler;
    private OpenSoloRoomHandler openSoloRoomHandler;
    private StartRunningHandler handler;

    @BeforeEach
    void setUp() {
        signUpHandler = newSignUpHandler();
        completeOnboardingHandler = new CompleteOnboardingHandler(
                userStore,        // LoadUserByIdPort
                onboardingStore,  // ExistsOnboardingPort
                onboardingStore,  // CheckNicknameDuplicatePort
                onboardingStore   // SaveOnboardingPort
        );
        openSoloRoomHandler = new OpenSoloRoomHandler(
                runningStore,     // ExistsActiveRunningPlayerPort
                onboardingStore,  // LoadUserAvgPacePort
                runningStore,     // CreateRunningPlayerPort
                runningStore      // CreateRunningRoomPort
        );
        handler = new StartRunningHandler(
                runningStore,     // LockRunningPlayerPort
                runningStore,     // LockRunningRoomPort
                runningStore,     // UpdateRunningRoomPort
                runningStore      // UpdateRunningPlayerPort
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

    // 솔로 개시 → 방 ID. 여기까지가 RUNNING_START의 전제다
    private Long openSoloRoom(UUID userId) {
        return openSoloRoomHandler.handle(new OpenSoloRoomCommand(userId)).runningRoomId();
    }

    private RunningRoom storedRoom(Long runningRoomId) {
        return runningStore.findRoom(runningRoomId).orElseThrow();
    }

    private RunningPlayer storedPlayer(Long runningRoomId) {
        RunningPlayerId playerId = storedRoom(runningRoomId).getSessions().get(0)
                .getRunningPlayerId();
        return runningStore.findPlayer(playerId.value()).orElseThrow();
    }

    @Test
    @DisplayName("솔로 방을 연 뒤 시작하면 방과 참가자가 함께 시작 상태가 된다")
    void startsSoloRoom() {
        // given -> 개시 직후는 MATCHED / JOINED다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = openSoloRoom(userId);
        assertThat(storedRoom(runningRoomId).getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
        assertThat(storedPlayer(runningRoomId).getStatus()).isEqualTo(RunningPlayerStatus.JOINED);

        // when
        StartRunningResult result = handler.handle(new StartRunningCommand(userId, runningRoomId));

        // then -> 저장소까지 반영돼야 한다(핸들러가 update를 빼먹으면 여기서 걸린다)
        assertThat(result.runningRoomId()).isEqualTo(runningRoomId);
        assertThat(storedRoom(runningRoomId).getStatus()).isEqualTo(RunningRoomStatus.STARTED);
        assertThat(storedPlayer(runningRoomId).getStatus()).isEqualTo(RunningPlayerStatus.RUNNING);
    }

    @Test
    @DisplayName("두 번 보내도 상태와 인원이 그대로다")
    void isIdempotent() {
        // given -> 재연결하면 클라는 같은 메시지를 다시 보낸다
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        Long runningRoomId = openSoloRoom(userId);
        handler.handle(new StartRunningCommand(userId, runningRoomId));

        // when
        handler.handle(new StartRunningCommand(userId, runningRoomId));

        // then
        RunningRoom room = storedRoom(runningRoomId);
        assertThat(room.getStatus()).isEqualTo(RunningRoomStatus.STARTED);
        assertThat(room.getPlayerCount().current()).isEqualTo(1);
        assertThat(room.getSessions()).hasSize(1);
        assertThat(storedPlayer(runningRoomId).getStatus()).isEqualTo(RunningPlayerStatus.RUNNING);
    }

    @Test
    @DisplayName("없는 방으로는 시작하지 못한다")
    void cannotStartUnknownRoom() {
        // given
        UUID userId = onboardedUser(EMAIL, NICKNAME);
        openSoloRoom(userId);

        // when & then
        assertThatThrownBy(() -> handler.handle(new StartRunningCommand(userId, 999L)))
                .isInstanceOf(RunningRoomNotFoundException.class);
    }

    @Test
    @DisplayName("남의 방으로는 시작하지 못한다")
    void cannotStartOthersRoom() {
        // given -> 둘 다 자기 솔로 방을 갖고 있다
        UUID other = onboardedUser(EMAIL, NICKNAME);
        Long othersRoomId = openSoloRoom(other);
        UUID me = onboardedUser("me@runiverse.com", "완두콩");
        openSoloRoom(me);

        // when & then -> 내 활성 신청은 저 방의 세션에 없다
        assertThatThrownBy(() -> handler.handle(new StartRunningCommand(me, othersRoomId)))
                .isInstanceOf(NotRoomPlayerException.class);

        // 거절된 요청은 남의 방을 건드리지 않는다
        assertThat(storedRoom(othersRoomId).getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
    }
}
