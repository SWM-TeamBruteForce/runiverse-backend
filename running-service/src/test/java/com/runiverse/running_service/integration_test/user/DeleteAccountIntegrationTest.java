package com.runiverse.running_service.integration_test.user;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.accountdeletion.AccountDeletionProperties;
import com.runiverse.running_service.application.user.command.accountdeletion.DeleteAccountCommand;
import com.runiverse.running_service.application.user.command.accountdeletion.DeleteAccountHandler;
import com.runiverse.running_service.application.user.command.accountdeletion.DeletedUserRedactor;
import com.runiverse.running_service.application.user.command.accountdeletion.KakaoUnlinkRequestedEvent;
import com.runiverse.running_service.application.user.command.accountdeletion.RedactDeletedUsersHandler;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageContentType;
import com.runiverse.running_service.application.user.command.profileimage.ProfileImageKeyPolicy;
import com.runiverse.running_service.application.user.port.out.AccountSnapshot;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.Gender;
import com.runiverse.running_service.domain.user.vo.LoginType;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.integration_test.fake.FakeProfileImageStorage;
import com.runiverse.running_service.integration_test.fake.InMemoryAccessTokenBlacklist;
import com.runiverse.running_service.integration_test.fake.InMemoryDeletedUserStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("회원탈퇴 통합 테스트")
public class DeleteAccountIntegrationTest {

    private static final String EMAIL = "runner@example.com";
    private static final String NICKNAME = "러너킴";
    private static final String ACCESS_TOKEN_ID = "jti-access-1";
    private static final String REFRESH_TOKEN_HASH = "hashed-refresh-token";
    private static final String PROVIDER_ID = "kakao-12345";
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 1, 1, 9, 0);
    private static final Duration RETENTION = Duration.ofDays(90);

    private UUID userId;
    private InMemoryDeletedUserStore deletedUserStore;
    private InMemoryRefreshTokenStore refreshTokenStore;
    private InMemoryAccessTokenBlacklist accessTokenBlacklist;
    private FakeProfileImageStorage profileImageStorage;
    private List<Object> publishedEvents;
    private List<UUID> settledUserIds;
    private DeleteAccountHandler deleteAccountHandler;
    private RedactDeletedUsersHandler redactHandler;

    @BeforeEach
    void setUp() {
        userId = UuidCreator.getTimeOrderedEpoch();
        deletedUserStore = new InMemoryDeletedUserStore();
        refreshTokenStore = new InMemoryRefreshTokenStore();
        accessTokenBlacklist = new InMemoryAccessTokenBlacklist();
        profileImageStorage = new FakeProfileImageStorage();
        publishedEvents = new ArrayList<>();
        settledUserIds = new ArrayList<>();

        deleteAccountHandler = new DeleteAccountHandler(
                deletedUserStore,                                 // LoadAccountSnapshotPort
                command -> settledUserIds.add(command.userId()),  // SettleRunningForAccountDeletionUsecase
                deletedUserStore,                                 // SaveDeletedUserPort
                deletedUserStore,                                 // DeleteUserPort
                refreshTokenStore,                                // DeleteRefreshTokenPort
                accessTokenBlacklist,                             // BlockAccessTokenPort
                publishedEvents::add                              // ApplicationEventPublisher
        );
        redactHandler = new RedactDeletedUsersHandler(
                deletedUserStore,  // LoadDeletedUserIdsPort
                new DeletedUserRedactor(
                        profileImageStorage,  // DeleteProfileImagesPort
                        deletedUserStore      // RedactDeletedUserPort
                ),
                new AccountDeletionProperties(RETENTION)
        );
    }

    // 온보딩까지 마치고 로그인해 있는 상태를 만든다
    private void givenOnboardedAccount(Provider provider) {
        deletedUserStore.register(new AccountSnapshot(
                userId, EMAIL, JOINED_AT,
                provider, provider == null ? null : PROVIDER_ID,
                NICKNAME, Gender.MALE, LocalDate.of(1995, 3, 1),
                330, new BigDecimal("70.0"), new BigDecimal("175.0")));
        refreshTokenStore.save(new UserId(userId), REFRESH_TOKEN_HASH);
    }

    // 가입만 하고 온보딩 전에 떠나는 경우다
    private void givenNotOnboardedAccount() {
        deletedUserStore.register(new AccountSnapshot(
                userId, EMAIL, JOINED_AT,
                null, null,
                null, null, null, null, null, null));
    }

    private void deleteAccount() {
        deleteAccountHandler.handle(new DeleteAccountCommand(userId, ACCESS_TOKEN_ID));
    }

    // 보관 기간이 지난 시점에 탈퇴한 것으로 만든다
    private void givenRetentionExpired() {
        deletedUserStore.snapshotAt(LocalDateTime.now().minus(RETENTION).minusDays(1));
    }

    @Test
    @DisplayName("탈퇴하면 계정은 사라지고 스냅샷만 남는다")
    void deletesAccountAndKeepsSnapshot() {
        // given
        givenOnboardedAccount(null);

        // when
        deleteAccount();

        // then -> 계정은 하드 삭제하고 통계에 쓸 값만 옮긴다
        assertThat(deletedUserStore.findAccount(userId)).isEmpty();
        InMemoryDeletedUserStore.DeletedRow row = deletedUserStore.findDeleted(userId).orElseThrow();
        assertThat(row.email()).contains(EMAIL);
        assertThat(row.nickname()).contains(NICKNAME);
        assertThat(row.loginType()).isEqualTo(LoginType.LOCAL);
        assertThat(row.joinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("체중·신장과 생년월일은 파생값으로 바꿔 담는다")
    void keepsDerivedValuesOnly() {
        // given
        givenOnboardedAccount(null);

        // when
        deleteAccount();

        // then -> 70kg / 1.75m^2 = 22.9, 생년월일은 연도만 남는다
        InMemoryDeletedUserStore.DeletedRow row = deletedUserStore.findDeleted(userId).orElseThrow();
        assertThat(row.bmi()).hasValueSatisfying(
                bmi -> assertThat(bmi).isEqualByComparingTo("22.9"));
        assertThat(row.birthYear()).contains(1995);
        assertThat(row.avgPace()).contains(330);
        assertThat(row.gender()).contains(Gender.MALE);
    }

    @Test
    @DisplayName("탈퇴하면 진행 중인 러닝을 정리하고 토큰을 폐기한다")
    void settlesRunningAndRevokesTokens() {
        // given
        givenOnboardedAccount(null);

        // when
        deleteAccount();

        // then
        assertThat(settledUserIds).containsExactly(userId);
        assertThat(refreshTokenStore.loadById(userId)).isEmpty();
        assertThat(accessTokenBlacklist.isBlocked(ACCESS_TOKEN_ID)).isTrue();
    }

    @Test
    @DisplayName("카카오 계정은 가입 수단을 남기고 연동 해제를 요청한다")
    void requestsKakaoUnlink() {
        // given
        givenOnboardedAccount(Provider.KAKAO);

        // when
        deleteAccount();

        // then -> 연동 해제는 커밋 뒤에 돌도록 이벤트로 넘긴다
        assertThat(deletedUserStore.findDeleted(userId).orElseThrow().loginType())
                .isEqualTo(LoginType.KAKAO);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.getFirst()).isInstanceOf(KakaoUnlinkRequestedEvent.class);
        assertThat(((KakaoUnlinkRequestedEvent) publishedEvents.getFirst()).providerId().value())
                .isEqualTo(PROVIDER_ID);
    }

    @Test
    @DisplayName("보관 기간이 지나기 전에는 배치가 아무것도 비우지 않는다")
    void keepsIdentityBeforeRetentionExpires() {
        // given -> 방금 탈퇴한 기록이다
        givenOnboardedAccount(null);
        deleteAccount();

        // when
        redactHandler.redactAfterRetention();

        // then
        InMemoryDeletedUserStore.DeletedRow row = deletedUserStore.findDeleted(userId).orElseThrow();
        assertThat(row.email()).contains(EMAIL);
        assertThat(row.nickname()).contains(NICKNAME);
    }

    @Test
    @DisplayName("보관 기간이 지나면 신원 정보만 비우고 통계 값은 남긴다")
    void redactsIdentityAfterRetention() {
        // given
        givenOnboardedAccount(null);
        givenRetentionExpired();
        deleteAccount();

        // when
        redactHandler.redactAfterRetention();

        // then -> 행은 남는다. 신고 대응용 둘만 비우고 나머지는 통계로 계속 쓴다
        assertThat(deletedUserStore.deletedCount()).isEqualTo(1);
        InMemoryDeletedUserStore.DeletedRow row = deletedUserStore.findDeleted(userId).orElseThrow();
        assertThat(row.email()).isEmpty();
        assertThat(row.nickname()).isEmpty();
        assertThat(row.gender()).contains(Gender.MALE);
        assertThat(row.birthYear()).contains(1995);
        assertThat(row.avgPace()).contains(330);
        assertThat(row.bmi()).isPresent();
        assertThat(row.loginType()).isEqualTo(LoginType.LOCAL);
        assertThat(row.joinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("보관 기간이 지나면 프로필 사진도 함께 지운다")
    void deletesProfileImagesAfterRetention() {
        // given -> 사진을 여러 번 바꿔 객체가 쌓인 상태다
        String oldKey = ProfileImageKeyPolicy.create(
                userId, UuidCreator.getTimeOrderedEpoch(), ProfileImageContentType.JPEG).value();
        String currentKey = ProfileImageKeyPolicy.create(
                userId, UuidCreator.getTimeOrderedEpoch(), ProfileImageContentType.JPEG).value();
        profileImageStorage.put(oldKey);
        profileImageStorage.put(currentKey);
        String otherUsersKey = ProfileImageKeyPolicy.create(
                UuidCreator.getTimeOrderedEpoch(), UuidCreator.getTimeOrderedEpoch(),
                ProfileImageContentType.JPEG).value();
        profileImageStorage.put(otherUsersKey);
        givenOnboardedAccount(null);
        givenRetentionExpired();
        deleteAccount();

        // when
        redactHandler.redactAfterRetention();

        // then -> 해당 유저의 프리픽스만 지운다
        assertThat(profileImageStorage.contains(oldKey)).isFalse();
        assertThat(profileImageStorage.contains(currentKey)).isFalse();
        assertThat(profileImageStorage.contains(otherUsersKey)).isTrue();
    }

    @Test
    @DisplayName("배치를 다시 돌려도 이미 비운 기록은 걸리지 않는다")
    void doesNotReprocessRedactedRecords() {
        // given
        givenOnboardedAccount(null);
        givenRetentionExpired();
        deleteAccount();
        redactHandler.redactAfterRetention();

        // when -> 다음 날 배치가 또 돈다
        redactHandler.redactAfterRetention();

        // then -> 사진 삭제를 두 번 부르지 않는다
        assertThat(profileImageStorage.deletedPrefixes())
                .containsExactly(ProfileImageKeyPolicy.prefixOf(userId));
    }

    @Test
    @DisplayName("온보딩 전에 탈퇴해도 가입 수단과 체류 기간은 남는다")
    void keepsLoginTypeAndPeriodForNotOnboardedAccount() {
        // given
        givenNotOnboardedAccount();
        givenRetentionExpired();
        deleteAccount();

        // when -> 온보딩 전 탈퇴는 nickname이 원래 null이라 email이 유일한 판별 기준이다
        redactHandler.redactAfterRetention();

        // then
        InMemoryDeletedUserStore.DeletedRow row = deletedUserStore.findDeleted(userId).orElseThrow();
        assertThat(row.email()).isEmpty();
        assertThat(row.gender()).isEmpty();
        assertThat(row.birthYear()).isEmpty();
        assertThat(row.avgPace()).isEmpty();
        assertThat(row.bmi()).isEmpty();
        assertThat(row.loginType()).isEqualTo(LoginType.LOCAL);
        assertThat(row.joinedAt()).isEqualTo(JOINED_AT);
    }
}
