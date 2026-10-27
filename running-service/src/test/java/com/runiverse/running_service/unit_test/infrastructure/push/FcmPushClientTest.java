package com.runiverse.running_service.unit_test.infrastructure.push;

import ch.qos.logback.classic.Level;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushSendStatus;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FCM 발송 클라이언트 단위 테스트")
public class FcmPushClientTest {

    private static final PushToken TOKEN = new PushToken("token-1");
    private static final PushMessage MESSAGE = new PushMessage("제목", "본문", Map.of("type", "TEST"));

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

        // when
        PushSendStatus status = client.send(TOKEN, MESSAGE);

        // then -> Message에 공개 getter가 없어 FCM에 실리는 JSON으로 본다
        assertThat(status).isEqualTo(PushSendStatus.SENT);
        verify(firebaseMessaging).send(captor.capture());
        String json = toJson(captor.getValue());
        assertThat(json)
                .contains("\"token\":\"token-1\"")
                .contains("\"title\":\"제목\"")
                .contains("\"body\":\"본문\"")
                .contains("\"type\":\"TEST\"")
                .contains("\"priority\":\"high\"")
                .contains("\"sound\":\"default\"");
    }

    @Test
    @DisplayName("등록 해제된 토큰이면 UNREGISTERED를 돌려주고 ERROR를 남기지 않는다")
    void unregisteredTokenIsNotServerError() throws Exception {
        // given
        FirebaseMessagingException exception = fcmException(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any())).thenThrow(exception);

        // when
        PushSendStatus status = client.send(TOKEN, MESSAGE);

        // then -> 앱 삭제·토큰 만료는 서버 문제가 아니다
        assertThat(status).isEqualTo(PushSendStatus.UNREGISTERED);
        assertThat(log.messages(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("그 밖의 FCM 오류는 FAILED를 돌려주고 ERROR를 남긴다")
    void otherErrorIsLogged() throws Exception {
        // given
        FirebaseMessagingException exception = fcmException(MessagingErrorCode.UNAVAILABLE);
        when(exception.getCause()).thenReturn(new IOException("response body with token-1"));
        when(firebaseMessaging.send(any())).thenThrow(exception);

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
    void errorWithoutCauseLogsCaughtType() throws Exception {
        // given -> 라이브러리가 원인 없이 FCM 오류만 던졌다
        FirebaseMessagingException exception = fcmException(MessagingErrorCode.INTERNAL);
        when(firebaseMessaging.send(any())).thenThrow(exception);

        // when
        client.send(TOKEN, MESSAGE);

        // then
        assertThat(log.messages(Level.ERROR)).singleElement().asString()
                .contains("cause=FirebaseMessagingException");
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

    private String toJson(Message message) throws IOException {
        return GsonFactory.getDefaultInstance().toString(message);
    }
}
