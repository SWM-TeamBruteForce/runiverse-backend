package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.port.out.BlockAccessTokenPort;
import com.runiverse.running_service.application.auth.port.out.DeleteRefreshTokenPort;
import com.runiverse.running_service.application.running.command.accountdeletion.SettleRunningForAccountDeletionCommand;
import com.runiverse.running_service.application.running.port.in.SettleRunningForAccountDeletionUsecase;
import com.runiverse.running_service.application.user.command.accountdeletion.DeleteAccountCommand;
import com.runiverse.running_service.application.user.command.accountdeletion.DeleteAccountHandler;
import com.runiverse.running_service.application.user.command.accountdeletion.KakaoUnlinkRequestedEvent;
import com.runiverse.running_service.application.running.exception.RunningSessionUnavailableException;
import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
import com.runiverse.running_service.application.user.exception.AccountDeletionUnavailableException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.AccountSnapshot;
import com.runiverse.running_service.application.user.port.out.DeleteUserPort;
import com.runiverse.running_service.application.user.port.out.LoadAccountSnapshotPort;
import com.runiverse.running_service.application.user.port.out.SaveDeletedUserPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.DeletedUser;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.ProviderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원탈퇴 단위 테스트")
public class DeleteAccountHandlerTest {

    private static final String ACCESS_TOKEN_ID = "jti-access-1";
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 1, 1, 9, 0);

    @Mock
    private LoadAccountSnapshotPort loadAccountSnapshotPort;
    @Mock
    private SettleRunningForAccountDeletionUsecase settleRunningForAccountDeletionUsecase;
    @Mock
    private SaveDeletedUserPort saveDeletedUserPort;
    @Mock
    private DeleteUserPort deleteUserPort;
    @Mock
    private DeleteRefreshTokenPort deleteRefreshTokenPort;
    @Mock
    private BlockAccessTokenPort blockAccessTokenPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DeleteAccountHandler handler;

    @Test
    @DisplayName("스냅샷을 남긴 뒤에 계정을 지운다")
    void savesSnapshotBeforeDeletingAccount() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(onboardedSnapshot(userId)));

        // when
        handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));

        // then -> 계정을 먼저 지우면 옮길 값이 사라진다
        InOrder order = inOrder(saveDeletedUserPort, deleteUserPort);
        order.verify(saveDeletedUserPort).saveDeletedUser(any());
        order.verify(deleteUserPort).deleteUser(new UserId(userId));
    }

    @Test
    @DisplayName("계정을 지우기 전에 진행 중인 러닝을 정리한다")
    void settlesRunningBeforeDeletingAccount() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(notOnboardedSnapshot(userId)));

        // when
        handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));

        // then
        InOrder order = inOrder(settleRunningForAccountDeletionUsecase, deleteUserPort);
        order.verify(settleRunningForAccountDeletionUsecase)
                .handle(new SettleRunningForAccountDeletionCommand(userId));
        order.verify(deleteUserPort).deleteUser(new UserId(userId));
    }

    @Test
    @DisplayName("온보딩을 마친 계정은 파생값까지 스냅샷에 담는다")
    void snapshotKeepsDerivedValuesWhenOnboarded() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(onboardedSnapshot(userId)));

        // when
        handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));

        // then -> 체중·신장은 BMI로, 생년월일은 연도로 바뀐다
        ArgumentCaptor<DeletedUser> captor = ArgumentCaptor.forClass(DeletedUser.class);
        verify(saveDeletedUserPort).saveDeletedUser(captor.capture());
        DeletedUser saved = captor.getValue();
        assertThat(saved.getBirthYear()).contains(1995);
        assertThat(saved.getBmi()).isPresent();
        assertThat(saved.getLoginType()).isEqualTo(LoginType.KAKAO);
    }

    @Test
    @DisplayName("온보딩 전 계정은 가입 수단만 남기고 온보딩 값은 비운다")
    void snapshotLeavesOnboardingEmptyWhenNotOnboarded() {
        // given -> 소셜 연동이 없으면 LOCAL이다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(notOnboardedSnapshot(userId)));

        // when
        handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));

        // then
        ArgumentCaptor<DeletedUser> captor = ArgumentCaptor.forClass(DeletedUser.class);
        verify(saveDeletedUserPort).saveDeletedUser(captor.capture());
        DeletedUser saved = captor.getValue();
        assertThat(saved.getLoginType()).isEqualTo(LoginType.LOCAL);
        assertThat(saved.getNickname()).isEmpty();
        assertThat(saved.getBmi()).isEmpty();
    }

    @Test
    @DisplayName("리프레시 토큰을 지우고 현재 액세스 토큰을 차단한다")
    void revokesBothTokens() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(notOnboardedSnapshot(userId)));

        // when
        handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));

        // then -> 액세스 토큰은 남은 유효 기간 동안 살아 있어 따로 막아야 한다
        verify(deleteRefreshTokenPort).delete(new UserId(userId));
        verify(blockAccessTokenPort).block(ACCESS_TOKEN_ID);
    }

    @Test
    @DisplayName("카카오 계정이면 연동 해제를 요청한다")
    void requestsKakaoUnlinkWhenKakaoAccount() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(onboardedSnapshot(userId)));

        // when
        handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));

        // then -> 커밋 뒤에 끊는다. oauth_users를 지우기 전에 확보한 provider_id를 쓴다
        verify(eventPublisher).publishEvent(
                new KakaoUnlinkRequestedEvent(new ProviderId("kakao-1")));
    }

    @Test
    @DisplayName("로컬 계정이면 연동 해제를 요청하지 않는다")
    void skipsKakaoUnlinkWhenLocalAccount() {
        // given -> 소셜 연동이 없으면 끊을 대상도 없다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(notOnboardedSnapshot(userId)));

        // when
        handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));

        // then
        verify(eventPublisher, never()).publishEvent(any(KakaoUnlinkRequestedEvent.class));
    }

    @Test
    @DisplayName("계정이 없으면 아무것도 지우지 않는다")
    void deletesNothingWhenAccountMissing() {
        // given -> 인증된 토큰인데 행이 없는 것은 데이터 이상이다
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(unknownUserId)))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                handler.handle(new DeleteAccountCommand(unknownUserId, ACCESS_TOKEN_ID)))
                .isInstanceOf(UserNotFoundException.class);
        verify(saveDeletedUserPort, never()).saveDeletedUser(any());
        verify(deleteUserPort, never()).deleteUser(any());
    }

    @ParameterizedTest
    @MethodSource("runningStoreFailures")
    @DisplayName("러닝 정리 중 저장소 장애는 탈퇴용 일시 장애로 바꿔 던지고 계정을 지우지 않는다")
    void wrapsRunningStoreFailure(RuntimeException failure) {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadAccountSnapshotPort.loadAccountSnapshot(new UserId(userId)))
                .thenReturn(Optional.of(onboardedSnapshot(userId)));
        doThrow(failure).when(settleRunningForAccountDeletionUsecase).handle(any());

        // when & then - WS용 코드·문구가 탈퇴 응답으로 새어 나가지 않는다
        assertThatThrownBy(() -> handler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID)))
                .isInstanceOf(AccountDeletionUnavailableException.class);
        verify(deleteUserPort, never()).deleteUser(any());
    }

    private static Stream<RuntimeException> runningStoreFailures() {
        return Stream.of(new RunningTrackUnavailableException(), new RunningSessionUnavailableException());
    }

    private AccountSnapshot onboardedSnapshot(UUID userId) {
        return new AccountSnapshot(
                userId, "runner@example.com", JOINED_AT,
                Provider.KAKAO, "kakao-1",
                "러너킴", Gender.MALE, LocalDate.of(1995, 3, 1),
                330, new BigDecimal("70.0"), new BigDecimal("175.0"));
    }

    private AccountSnapshot notOnboardedSnapshot(UUID userId) {
        return new AccountSnapshot(
                userId, "runner@example.com", JOINED_AT,
                null, null,
                null, null, null, null, null, null);
    }
}
