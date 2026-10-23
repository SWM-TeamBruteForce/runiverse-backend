package com.runiverse.running_service.integration_test;

import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationHandler;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpHandler;
import com.runiverse.running_service.application.auth.command.signup.SignUpUserRegistrar;
import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.command.combo.UpdateRunningComboJudge;
import com.runiverse.running_service.integration_test.fake.FakeEmailSender;
import com.runiverse.running_service.integration_test.fake.FakeRunningComboPublisher;
import com.runiverse.running_service.integration_test.fake.FakeRunningProgressPublisher;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningComboStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningDistanceStore;
import com.runiverse.running_service.integration_test.fake.InMemoryLiveRunningStatusStore;
import com.runiverse.running_service.integration_test.fake.FakeGpsTrackUploader;
import com.runiverse.running_service.integration_test.fake.FakeWeatherProvider;
import com.runiverse.running_service.integration_test.fake.FakeAppleOauthClient;
import com.runiverse.running_service.integration_test.fake.FakeGoogleOauthClient;
import com.runiverse.running_service.integration_test.fake.FakeKakaoOauthClient;
import com.runiverse.running_service.integration_test.fake.FakePasswordHasher;
import com.runiverse.running_service.integration_test.fake.FakeTokenProvider;
import com.runiverse.running_service.integration_test.fake.FakeUserIdGenerator;
import com.runiverse.running_service.integration_test.fake.FakeVerificationCodeGenerator;
import com.runiverse.running_service.integration_test.fake.FakeVerificationCodeHasher;
import com.runiverse.running_service.integration_test.fake.FakeVerificationTicketGenerator;
import com.runiverse.running_service.integration_test.fake.FakeVerificationTicketHasher;
import com.runiverse.running_service.integration_test.fake.InMemoryAccessTokenBlacklist;
import com.runiverse.running_service.integration_test.fake.InMemoryEmailVerificationStore;
import com.runiverse.running_service.integration_test.fake.InMemoryOnboardingStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRefreshTokenStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningRecordStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningStore;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningTrackStore;
import com.runiverse.running_service.integration_test.fake.InMemoryUserStore;
import com.runiverse.running_service.integration_test.fake.InMemoryVerificationTicketStore;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import com.runiverse.running_service.application.running.common.RunningComboArchiver;
import com.runiverse.running_service.application.running.common.RunningComboWriter;
import com.runiverse.running_service.integration_test.fake.InMemoryRunningComboArchiveStore;

public abstract class IntegrationTestSupport {

    // 실제 application.properties와 같은 값으로 맞춘다
    protected static final int MAX_ATTEMPTS = 5;
    protected static final int DAILY_LIMIT = 10;
    protected static final RunningComboProperties COMBO_PROPERTIES = new RunningComboProperties(
            30, 30, Duration.ofSeconds(19), Duration.ofSeconds(10), 1);

    protected InMemoryUserStore userStore;
    protected InMemoryRefreshTokenStore refreshTokenStore;
    protected InMemoryOnboardingStore onboardingStore;
    protected InMemoryRunningStore runningStore;
    protected InMemoryRunningTrackStore runningTrackStore;
    protected InMemoryRunningDistanceStore runningDistanceStore;
    protected InMemoryLiveRunningStatusStore liveRunningStatusStore;
    protected FakeRunningProgressPublisher runningProgressPublisher;
    protected InMemoryRunningComboStore runningComboStore;
    protected InMemoryRunningComboArchiveStore runningComboArchiveStore;
    protected FakeRunningComboPublisher runningComboPublisher;
    protected InMemoryRunningRecordStore runningRecordStore;
    protected FakeGpsTrackUploader gpsTrackUploader;
    protected FakeWeatherProvider weatherProvider;
    protected InMemoryAccessTokenBlacklist accessTokenBlacklist;
    protected InMemoryVerificationTicketStore verificationTicketStore;
    protected InMemoryEmailVerificationStore emailVerificationStore;
    protected FakePasswordHasher passwordHasher;
    protected FakeTokenProvider tokenProvider;
    protected FakeUserIdGenerator userIdGenerator;
    protected FakeGoogleOauthClient googleOauthClient;
    protected FakeKakaoOauthClient kakaoOauthClient;
    protected FakeAppleOauthClient appleOauthClient;
    protected FakeVerificationTicketHasher verificationTicketHasher;
    protected FakeVerificationTicketGenerator verificationTicketGenerator;
    protected FakeVerificationCodeGenerator verificationCodeGenerator;
    protected FakeVerificationCodeHasher verificationCodeHasher;
    protected FakeEmailSender emailSender;

    @BeforeEach
    void setUpFakes() {
        userStore = new InMemoryUserStore();
        refreshTokenStore = new InMemoryRefreshTokenStore();
        onboardingStore = new InMemoryOnboardingStore();
        runningStore = new InMemoryRunningStore();
        runningTrackStore = new InMemoryRunningTrackStore();
        runningDistanceStore = new InMemoryRunningDistanceStore();
        liveRunningStatusStore = new InMemoryLiveRunningStatusStore();
        runningProgressPublisher = new FakeRunningProgressPublisher();
        runningComboStore = new InMemoryRunningComboStore();
        runningComboArchiveStore = new InMemoryRunningComboArchiveStore();
        runningComboPublisher = new FakeRunningComboPublisher();
        runningRecordStore = new InMemoryRunningRecordStore();
        gpsTrackUploader = new FakeGpsTrackUploader();
        weatherProvider = new FakeWeatherProvider();
        accessTokenBlacklist = new InMemoryAccessTokenBlacklist();
        verificationTicketStore = new InMemoryVerificationTicketStore();
        emailVerificationStore = new InMemoryEmailVerificationStore(MAX_ATTEMPTS, DAILY_LIMIT);
        passwordHasher = new FakePasswordHasher();
        tokenProvider = new FakeTokenProvider();
        userIdGenerator = new FakeUserIdGenerator();
        googleOauthClient = new FakeGoogleOauthClient();
        kakaoOauthClient = new FakeKakaoOauthClient();
        appleOauthClient = new FakeAppleOauthClient();
        verificationTicketHasher = new FakeVerificationTicketHasher();
        verificationTicketGenerator = new FakeVerificationTicketGenerator();
        verificationCodeGenerator = new FakeVerificationCodeGenerator();
        verificationCodeHasher = new FakeVerificationCodeHasher();
        emailSender = new FakeEmailSender();
    }

    // 위치 배치 핸들러가 콤보 판정을 물고 있어 러닝 통합 테스트마다 같은 조립이 필요하다.
    // 한 저장소가 포트 6개를 겸한다
    protected UpdateRunningComboJudge newUpdateRunningComboJudge() {
        return new UpdateRunningComboJudge(
                runningComboStore,      // LoadRunningComboSnapshotsPort
                runningComboStore,      // SaveRunningComboSnapshotPort
                runningComboStore,      // LoadRunningComboPairsPort
                runningComboStore,      // SaveRunningComboPairsPort
                runningComboStore,      // SaveRunningComboSegmentsPort
                runningComboStore,      // MarkRunningComboPausedPort
                runningComboPublisher,  // PublishRunningComboPort
                COMBO_PROPERTIES
        );
    }

    // 방이 닫힐 때 콤보를 옮기는 조립 — 러닝 종료·강제 종료가 함께 쓴다.
    // 페이크 조립은 트랜잭션 동기화가 없어 커밋 뒤 작업이 그 자리에서 돈다
    protected RunningComboArchiver newRunningComboArchiver() {
        return new RunningComboArchiver(
                new RunningComboWriter(
                        runningComboStore,        // LoadRunningComboHistoryPort
                        runningRecordStore,       // LoadRecordedRoutesPort
                        runningComboArchiveStore, // CreateRunningCombosPort
                        COMBO_PROPERTIES),
                runningComboStore);               // DeleteRunningComboStatePort
    }

    // 조립할 fake가 많고 여러 테스트가 가입부터 시작하므로 여기서 한 번만 엮는다
    protected SignUpHandler newSignUpHandler() {
        SignUpUserRegistrar registrar = new SignUpUserRegistrar(
                userStore,        // CheckEmailDuplicatePort
                passwordHasher,   // PasswordHashPort
                userIdGenerator,  // GenerateUserIdPort
                userStore         // SaveUserPort
        );
        return new SignUpHandler(
                verificationTicketHasher,  // VerificationTicketHashPort
                verificationTicketStore,   // ConsumeVerificationTicketPort
                registrar,
                tokenProvider,             // GenerateTokenPort
                tokenProvider,             // RefreshTokenHashPort
                refreshTokenStore          // SaveRefreshTokenHashPort
        );
    }

    // 발송 핸들러는 한 저장소가 포트 6개 중 5개를 겸한다
    protected SendEmailVerificationHandler newSendEmailVerificationHandler() {
        return new SendEmailVerificationHandler(
                emailVerificationStore,     // AcquireSendCooldownPort
                emailVerificationStore,     // ReleaseSendCooldownPort
                emailVerificationStore,     // CheckDailySendLimitPort
                userStore,                  // CheckEmailDuplicatePort
                verificationCodeGenerator,  // GenerateVerificationCodePort
                verificationCodeHasher,     // VerificationCodeHashPort
                emailVerificationStore,     // SaveVerificationCodePort
                emailVerificationStore,     // DeleteVerificationCodePort
                emailSender                 // SendEmailPort
        );
    }

    protected VerifyEmailCodeHandler newVerifyEmailCodeHandler() {
        return new VerifyEmailCodeHandler(
                emailVerificationStore,       // ConsumeVerificationAttemptPort
                verificationCodeHasher,       // VerificationCodeHashPort
                emailVerificationStore,       // DeleteVerificationCodePort
                verificationTicketGenerator,  // GenerateVerificationTicketPort
                verificationTicketStore,      // SaveVerificationTicketPort
                verificationTicketHasher      // VerificationTicketHashPort
        );
    }

    // 이메일 인증을 마친 상태를 만들고 원문 티켓을 돌려준다.
    // 회원가입은 이 티켓으로만 이메일을 얻으므로 가입 전에 반드시 필요하다
    protected String issueVerificationTicket(String email) {
        String ticket = "verification-ticket-" + email;
        verificationTicketStore.save(verificationTicketHasher.hash(ticket), email);
        return ticket;
    }
}
