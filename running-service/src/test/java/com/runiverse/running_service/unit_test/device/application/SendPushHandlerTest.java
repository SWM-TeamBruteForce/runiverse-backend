package com.runiverse.running_service.unit_test.device.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.port.out.LoadRefreshTokenPort;
import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.device.command.push.PushListener;
import com.runiverse.running_service.application.device.command.push.PushRequestedEvent;
import com.runiverse.running_service.application.device.command.push.PushTargetReader;
import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.command.push.SendPushHandler;
import com.runiverse.running_service.application.device.command.push.StaleDeviceDeactivator;
import com.runiverse.running_service.application.device.port.out.DeactivateStaleDevicePort;
import com.runiverse.running_service.application.device.port.out.LoadPushTargetPort;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushSendStatus;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.device.port.out.SendPushPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.device.vo.DeviceId;
import com.runiverse.running_service.domain.device.vo.PushToken;
import com.runiverse.running_service.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("푸시 발송 단위 테스트")
public class SendPushHandlerTest {

    @Mock
    private LoadPushTargetPort loadPushTargetPort;
    @Mock
    private LoadUserByIdPort loadUserByIdPort;
    @Mock
    private LoadRefreshTokenPort loadRefreshTokenPort;
    @Mock
    private SendPushPort sendPushPort;
    @Mock
    private DeactivateStaleDevicePort deactivateStaleDevicePort;

    private SendPushHandler handler;

    private final UUID userId = UuidCreator.getTimeOrderedEpoch();
    private final PushTarget target = new PushTarget(new DeviceId("device-1"), new PushToken("token-1"));
    private final PushMessage message = new PushMessage(
            PushType.RUNNING_REMINDER, "제목", "본문", Map.of("runningRoomId", "125"),
            LocalDateTime.now().plusMinutes(5));
    private final SendPushCommand command = new SendPushCommand(userId, message);

    // 읽기·기기 끄기 컴포넌트는 트랜잭션 경계만 나눈 것이라 포트 목을 감싼 진짜 객체로 넣는다
    @BeforeEach
    void setUp() {
        handler = new SendPushHandler(
                new PushTargetReader(loadPushTargetPort, loadUserByIdPort),
                loadRefreshTokenPort,
                sendPushPort,
                new StaleDeviceDeactivator(deactivateStaleDevicePort));
    }

    @Test
    @DisplayName("켜진 기기·알림 동의·로그인 세션이 모두 있으면 그 기기의 토큰으로 보낸다")
    void sendsToActiveDevice() {
        // given
        givenTarget();
        givenUser(true);
        givenSession();
        when(sendPushPort.send(any(), any(), any())).thenReturn(PushSendStatus.SENT);

        // when
        handler.handle(command);

        // then
        verify(sendPushPort).send(new UserId(userId), new PushToken("token-1"), message);
        verifyNoInteractions(deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("켜진 기기가 없으면 보내지 않는다")
    void skipsWithoutActiveDevice() {
        // given
        when(loadPushTargetPort.loadPushTarget(new UserId(userId))).thenReturn(Optional.empty());

        // when
        handler.handle(command);

        // then
        verifyNoInteractions(sendPushPort, deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("탈퇴한 유저에게는 보내지 않는다")
    void skipsDeletedUser() {
        // given
        givenTarget();
        when(loadUserByIdPort.loadById(new UserId(userId))).thenReturn(Optional.empty());

        // when
        handler.handle(command);

        // then
        verifyNoInteractions(sendPushPort, deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("알림을 끈 유저에게는 보내지 않는다")
    void skipsWhenAlertConsentOff() {
        // given
        givenTarget();
        givenUser(false);

        // when
        handler.handle(command);

        // then
        verifyNoInteractions(sendPushPort, deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("로그인 세션이 없으면 기기가 켜져 있어도 보내지 않는다")
    void skipsWithoutSession() {
        // given
        givenTarget();
        givenUser(true);
        when(loadRefreshTokenPort.load(new UserId(userId))).thenReturn(Optional.empty());

        // when
        handler.handle(command);

        // then -> 로그아웃 요청이 실패했거나 세션이 만료돼도 기기는 켜진 채 남을 수 있다
        verifyNoInteractions(sendPushPort, deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("등록 해제된 토큰이면 보낸 그 토큰으로 기기를 끈다")
    void deactivatesUnregisteredToken() {
        // given
        givenTarget();
        givenUser(true);
        givenSession();
        when(sendPushPort.send(any(), any(), any())).thenReturn(PushSendStatus.UNREGISTERED);

        // when
        handler.handle(command);

        // then -> 토큰을 함께 넘겨야 그사이 새 토큰을 등록한 기기를 끄지 않는다
        verify(deactivateStaleDevicePort).deactivateStale(new DeviceId("device-1"), new PushToken("token-1"));
    }

    @Test
    @DisplayName("그 밖의 발송 실패는 기기를 그대로 둔다")
    void keepsDeviceOnOtherFailure() {
        // given
        givenTarget();
        givenUser(true);
        givenSession();
        when(sendPushPort.send(any(), any(), any())).thenReturn(PushSendStatus.FAILED);

        // when
        handler.handle(command);

        // then -> FCM 장애로 끄면 장애가 끝나도 다시 등록할 때까지 푸시가 안 간다
        verifyNoInteractions(deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("만료 시각이 지났으면 기기·세션이 다 있어도 보내지 않는다")
    void skipsExpiredMessage() {
        // given -> 발송 대기 줄에서 기다리는 사이 시작 시각이 지났다
        givenTarget();
        givenUser(true);
        givenSession();
        PushMessage expired = new PushMessage(
                PushType.RUNNING_REMINDER, "제목", "본문", Map.of("runningRoomId", "125"),
                LocalDateTime.now().minusSeconds(1));

        // when
        handler.handle(new SendPushCommand(userId, expired));

        // then
        verifyNoInteractions(sendPushPort, deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("만료 시각이 없으면 시각과 무관하게 보낸다")
    void sendsMessageWithoutExpiry() {
        // given -> 늦게 닿아도 의미가 있는 알림이다
        givenTarget();
        givenUser(true);
        givenSession();
        PushMessage timeless = new PushMessage(PushType.RUNNING_REMINDER, "제목", "본문", Map.of(), null);
        when(sendPushPort.send(any(), any(), any())).thenReturn(PushSendStatus.SENT);

        // when
        handler.handle(new SendPushCommand(userId, timeless));

        // then
        verify(sendPushPort).send(new UserId(userId), new PushToken("token-1"), timeless);
    }

    @Test
    @DisplayName("보내려는 순간 만료됐다는 결과면 기기를 그대로 둔다")
    void keepsDeviceWhenExpiredAtSend() {
        // given
        givenTarget();
        givenUser(true);
        givenSession();
        when(sendPushPort.send(any(), any(), any())).thenReturn(PushSendStatus.EXPIRED);

        // when
        handler.handle(command);

        // then -> 토큰은 멀쩡하다
        verifyNoInteractions(deactivateStaleDevicePort);
    }

    @Test
    @DisplayName("발송 유스케이스는 트랜잭션을 열지 않고, DB 읽기와 기기 끄기만 각자 연다")
    void opensTransactionOnlyAroundDbWork() throws NoSuchMethodException {
        // when & then -> FCM을 기다리는 동안 DB 커넥션을 쥐지 않는다
        assertThat(SendPushHandler.class.getAnnotation(Transactional.class)).isNull();
        assertThat(SendPushHandler.class.getMethod("handle", SendPushCommand.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(PushListener.class.getMethod("send", PushRequestedEvent.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(PushTargetReader.class.getMethod("read", UserId.class, PushType.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(StaleDeviceDeactivator.class.getMethod("deactivate", PushTarget.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private void givenTarget() {
        when(loadPushTargetPort.loadPushTarget(new UserId(userId))).thenReturn(Optional.of(target));
    }

    private void givenUser(boolean alertConsent) {
        when(loadUserByIdPort.loadById(new UserId(userId)))
                .thenReturn(Optional.of(new User(userId, "runner@runiverse.com", "", alertConsent)));
    }

    private void givenSession() {
        when(loadRefreshTokenPort.load(new UserId(userId))).thenReturn(Optional.of("refresh-hash"));
    }
}
