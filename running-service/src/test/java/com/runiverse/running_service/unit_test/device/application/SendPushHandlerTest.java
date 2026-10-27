package com.runiverse.running_service.unit_test.device.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.port.out.LoadRefreshTokenPort;
import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.command.push.SendPushHandler;
import com.runiverse.running_service.application.device.port.out.DeactivateStaleDevicePort;
import com.runiverse.running_service.application.device.port.out.LoadPushTargetPort;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushSendResult;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import com.runiverse.running_service.application.device.port.out.SendPushPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.device.vo.DeviceId;
import com.runiverse.running_service.domain.device.vo.PushToken;
import com.runiverse.running_service.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    @InjectMocks
    private SendPushHandler handler;

    private final UUID userId = UuidCreator.getTimeOrderedEpoch();
    private final PushTarget target = new PushTarget(new DeviceId("device-1"), new PushToken("token-1"));
    private final SendPushCommand command = new SendPushCommand(userId, "제목", "본문", Map.of("type", "TEST"));

    @Test
    @DisplayName("켜진 기기·알림 동의·로그인 세션이 모두 있으면 그 기기의 토큰으로 보낸다")
    void sendsToActiveDevice() {
        // given
        givenTarget();
        givenUser(true);
        givenSession();
        when(sendPushPort.send(any(), any())).thenReturn(PushSendResult.SENT);

        // when
        handler.handle(command);

        // then
        verify(sendPushPort).send(new PushToken("token-1"), new PushMessage("제목", "본문", Map.of("type", "TEST")));
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
        when(sendPushPort.send(any(), any())).thenReturn(PushSendResult.UNREGISTERED);

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
        when(sendPushPort.send(any(), any())).thenReturn(PushSendResult.FAILED);

        // when
        handler.handle(command);

        // then -> FCM 장애로 끄면 장애가 끝나도 다시 등록할 때까지 푸시가 안 간다
        verifyNoInteractions(deactivateStaleDevicePort);
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
