package com.runiverse.running_service.unit_test.infrastructure.push;

import ch.qos.logback.classic.Level;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushSendStatus;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.domain.device.vo.PushToken;
import com.runiverse.running_service.infrastructure.push.FcmPushClient;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FCM 발송 클라이언트 단위 테스트")
public class FcmPushClientTest {

    private static final PushToken TOKEN = new PushToken("token-1");
    private static final PushMessage MESSAGE = new PushMessage(
            PushType.RUNNING_REMINDER, "제목", "본문", Map.of("runningRoomId", "125"), null);

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private FcmPushClient client;

    private LogCapture log;

    @BeforeEach
    void setUp() {
        log = LogCapture.of(FcmPushClient.class);
        client = new FcmPushClient(firebaseMessaging);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("토큰·제목·본문·데이터와 플랫폼별 옵션을 담아 보낸다")
    void sendBuildsMessage() throws Exception {
        // given
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("message-id"));

        // when
        PushSendStatus status = client.send(TOKEN, MESSAGE);

        // then -> Message에 공개 getter가 없어 FCM에 실리는 JSON으로 본다
        assertThat(status).isEqualTo(PushSendStatus.SENT);
        verify(firebaseMessaging).sendAsync(captor.capture());
        String json = toJson(captor.getValue());
        assertThat(json)
                .contains("\"token\":\"token-1\"")
                .contains("\"title\":\"제목\"")
                .contains("\"body\":\"본문\"")
                .contains("\"runningRoomId\":\"125\"")
                .contains("\"priority\":\"high\"")
                .contains("\"sound\":\"default\"");
    }

    @Test
    @DisplayName("푸시 종류를 data의 type으로 함께 싣는다")
    void sendPutsTypeIntoData() throws Exception {
        // given
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("message-id"));

        // when
        client.send(TOKEN, MESSAGE);

        // then -> 앱이 이 값으로 눌렀을 때 갈 화면을 고른다
        assertThat(sentJson()).contains("\"type\":\"RUNNING_REMINDER\"");
    }

    @Test
    @DisplayName("유효 시간이 있으면 안드로이드엔 남은 시간을, APNs엔 만료 시각을 싣는다")
    void sendPutsTtlForEachPlatform() throws Exception {
        // given
        PushMessage message = new PushMessage(
                PushType.RUNNING_REMINDER, "제목", "본문", Map.of("runningRoomId", "125"), Duration.ofMinutes(5));
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("message-id"));
        Instant before = Instant.now();

        // when
        client.send(TOKEN, message);

        // then -> APNs는 남은 시간이 아니라 epoch 초로 받는다
        Instant after = Instant.now();
        String json = sentJson();
        assertThat(json).contains("\"ttl\":\"300s\"");
        Matcher expiration = Pattern.compile("\"apns-expiration\":\"(\\d+)\"").matcher(json);
        assertThat(expiration.find()).isTrue();
        assertThat(Long.parseLong(expiration.group(1))).isBetween(
                before.plusSeconds(300).getEpochSecond(), after.plusSeconds(300).getEpochSecond());
    }

    @Test
    @DisplayName("유효 시간이 없으면 FCM 기본값을 쓰게 싣지 않는다")
    void sendOmitsTtlWhenAbsent() throws Exception {
        // given
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("message-id"));

        // when
        client.send(TOKEN, MESSAGE);

        // then
        assertThat(sentJson()).doesNotContain("\"ttl\"").doesNotContain("apns-expiration");
    }

    @Test
    @DisplayName("등록 해제된 토큰이면 UNREGISTERED를 돌려주고 ERROR를 남기지 않는다")
    void unregisteredTokenIsNotServerError() {
        // given
        FirebaseMessagingException exception = fcmException(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(exception));

        // when
        PushSendStatus status = client.send(TOKEN, MESSAGE);

        // then -> 앱 삭제·토큰 만료는 서버 문제가 아니다
        assertThat(status).isEqualTo(PushSendStatus.UNREGISTERED);
        assertThat(log.messages(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("그 밖의 FCM 오류는 FAILED를 돌려주고 ERROR를 남긴다")
    void otherErrorIsLogged() {
        // given
        FirebaseMessagingException exception = fcmException(MessagingErrorCode.UNAVAILABLE);
        when(exception.getCause()).thenReturn(new IOException("response body with token-1"));
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(exception));

        // when
        PushSendStatus status = client.send(TOKEN, MESSAGE);

        // then -> 원인 예외에 FCM 응답 본문이 담겨 와서 예외 객체를 넘기지 않는다
        assertThat(status).isEqualTo(PushSendStatus.FAILED);
        assertThat(log.messages(Level.ERROR)).singleElement().asString()
                .contains("[기기] 푸시 발송 실패: FCM 오류")
                .contains("messagingErrorCode=UNAVAILABLE")
                .contains("cause=IOException")
                .doesNotContain("token-1");
        assertThat(log.events(Level.ERROR).get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("FCM 오류에 원인 예외가 없으면 잡은 예외의 종류를 남긴다")
    void errorWithoutCauseLogsCaughtType() {
        // given -> 라이브러리가 원인 없이 FCM 오류만 던졌다
        FirebaseMessagingException exception = fcmException(MessagingErrorCode.INTERNAL);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(exception));

        // when
        client.send(TOKEN, MESSAGE);

        // then
        assertThat(log.messages(Level.ERROR)).singleElement().asString()
                .contains("cause=FirebaseMessagingException");
    }

    @Test
    @DisplayName("응답이 상한 안에 오지 않으면 기다리지 않고 FAILED를 돌려주고 재시도를 멈춘다")
    void timeoutIsFailed() {
        // given -> 라이브러리가 503 재시도로 응답을 붙잡고 있는 상황이다
        SettableApiFuture<String> future = SettableApiFuture.create();
        when(firebaseMessaging.sendAsync(any())).thenReturn(future);

        // when
        PushSendStatus status = client.send(TOKEN, MESSAGE);

        // then
        assertThat(status).isEqualTo(PushSendStatus.FAILED);
        assertThat(future.isCancelled()).isTrue();
        assertThat(log.messages(Level.ERROR)).singleElement().asString()
                .contains("[기기] 푸시 발송 실패: FCM 응답 지연")
                .contains("timeoutMs=3000");
    }

    // 생성자가 공개돼 있지 않아 mock으로 만든다
    private FirebaseMessagingException fcmException(MessagingErrorCode messagingErrorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(messagingErrorCode);
        if (messagingErrorCode != MessagingErrorCode.UNREGISTERED) {
            when(exception.getErrorCode()).thenReturn(ErrorCode.UNAVAILABLE);
        }
        return exception;
    }

    // Message에 공개 getter가 없어 FCM에 실리는 JSON으로 본다
    private String sentJson() throws IOException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).sendAsync(captor.capture());
        return toJson(captor.getValue());
    }

    private String toJson(Message message) throws IOException {
        return GsonFactory.getDefaultInstance().toString(message);
    }
}
