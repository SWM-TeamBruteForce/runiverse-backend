package com.runiverse.running_service.application.user.command.accountdeletion;

import com.runiverse.running_service.application.auth.port.out.BlockAccessTokenPort;
import com.runiverse.running_service.application.auth.port.out.DeleteRefreshTokenPort;
import com.runiverse.running_service.application.common.port.out.LockUserPort;
import com.runiverse.running_service.application.device.port.out.DeleteUserDevicesPort;
import com.runiverse.running_service.application.match.port.out.DeleteReceivedInvitationsPort;
import com.runiverse.running_service.application.running.command.accountdeletion.SettleRunningForAccountDeletionCommand;
import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
import com.runiverse.running_service.application.running.port.in.SettleRunningForAccountDeletionUsecase;
import com.runiverse.running_service.application.user.exception.AccountDeletionUnavailableException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.in.DeleteAccountUsecase;
import com.runiverse.running_service.application.user.port.out.AccountSnapshot;
import com.runiverse.running_service.application.user.port.out.DeleteUserPort;
import com.runiverse.running_service.application.user.port.out.LoadAccountSnapshotPort;
import com.runiverse.running_service.application.user.port.out.SaveDeletedUserPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.DeletedUser;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.ProviderId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteAccountHandler implements DeleteAccountUsecase {

    private final LockUserPort lockUserPort;
    private final LoadAccountSnapshotPort loadAccountSnapshotPort;
    private final SettleRunningForAccountDeletionUsecase settleRunningForAccountDeletionUsecase;
    private final SaveDeletedUserPort saveDeletedUserPort;
    private final DeleteUserDevicesPort deleteUserDevicesPort;
    private final DeleteReceivedInvitationsPort deleteReceivedInvitationsPort;
    private final DeleteUserPort deleteUserPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final BlockAccessTokenPort blockAccessTokenPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(DeleteAccountCommand command) {
        UserId userId = new UserId(command.userId());
        // 0. 같은 유저의 기기 등록과 줄 세운다 — 탈퇴 중에 기기 행이 새로 생기지 않게 한다
        lockUserPort.lock(userId);
        // 1. 지우기 전에 한 번에 읽는다 — 소셜 연동을 지운 뒤에는 login_type을 판정할 수 없다
        AccountSnapshot snapshot = loadAccountSnapshotPort.loadAccountSnapshot(userId)
                .orElseThrow(UserNotFoundException::new);
        // 2. 진행 중인 러닝·매칭을 먼저 정리한다. 트랙을 못 읽은 장애는 WS용 코드라 탈퇴용으로 바꿔 던진다
        try {
            settleRunningForAccountDeletionUsecase.handle(
                    new SettleRunningForAccountDeletionCommand(command.userId()));
        } catch (RunningTrackUnavailableException e) {
            throw new AccountDeletionUnavailableException();
        }
        // 3. 스냅샷을 남긴 뒤 계정을 지운다. 기기와 받은 초대는 FK CASCADE에 기대지 않고 먼저 지운다 —
        //    받은 초대는 2에서 방을 나가며 고칠 수 있는 행이라 CASCADE가 지우면 커밋 때 갱신이 실패한다
        saveDeletedUserPort.saveDeletedUser(toDeletedUser(snapshot));
        deleteUserDevicesPort.deleteAll(userId);
        deleteReceivedInvitationsPort.deleteAll(userId);
        deleteUserPort.deleteUser(userId);
        // 4. 토큰을 폐기한다 — 로그아웃과 같은 두 단계
        deleteRefreshTokenPort.delete(userId);
        blockAccessTokenPort.block(command.accessTokenId());
        // 5. 카카오 연동은 커밋 뒤에 끊는다 — 여기서 끊으면 탈퇴가 롤백돼도 되살릴 수 없다
        if (snapshot.provider() == Provider.KAKAO) {
            eventPublisher.publishEvent(
                    new KakaoUnlinkRequestedEvent(new ProviderId(snapshot.providerId())));
        }
    }

    private DeletedUser toDeletedUser(AccountSnapshot snapshot) {
        LoginType loginType = LoginType.from(snapshot.provider());
        if (!snapshot.hasOnboarded()) {
            return DeletedUser.ofNotOnboarded(
                    snapshot.userId(), snapshot.email(), loginType, snapshot.joinedAt());
        }
        return DeletedUser.ofOnboarded(
                snapshot.userId(), snapshot.email(),
                snapshot.nickname(), snapshot.gender(), snapshot.birthday(),
                snapshot.avgPace(), snapshot.weight(), snapshot.height(),
                loginType, snapshot.joinedAt());
    }
}
