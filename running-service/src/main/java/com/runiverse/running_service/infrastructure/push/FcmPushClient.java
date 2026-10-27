package com.runiverse.running_service.infrastructure.push;

import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushSendStatus;
import com.runiverse.running_service.application.device.port.out.SendPushPort;
import com.runiverse.running_service.domain.device.vo.PushToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// 안드로이드·iOS 모두 FCM 하나로 보낸다 — iOS는 FCM이 APNs로 넘긴다
@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class FcmPushClient implements SendPushPort {

    private static final String DEFAULT_SOUND = "default";
    // 라이브러리는 503이면 최대 4번, 매번 최대 60초씩 기다려 다시 보낸다.
    // 커밋 뒤 요청 스레드에서 보내므로 응답이 늦어지지 않게 전체를 이 시간으로 묶는다
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public PushSendStatus send(PushToken pushToken, PushMessage message) {
        ApiFuture<String> future = firebaseMessaging.sendAsync(toMessage(pushToken, message));
        try {
            future.get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return PushSendStatus.SENT;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FirebaseMessagingException fcmException) {
                return toStatus(fcmException);
            }
            // FCM 응답이 아닌 실패 — 메시지에 무엇이 담길지 알 수 없어 종류만 남긴다
            log.error("[기기] 푸시 발송 실패: FCM 처리 오류 - cause={}", e.getCause().getClass().getSimpleName());
            return PushSendStatus.FAILED;
        } catch (TimeoutException e) {
            // 뒤에서 계속되는 재시도도 멈춘다
            future.cancel(true);
            log.error("[기기] 푸시 발송 실패: FCM 응답 지연 - timeoutMs={}", SEND_TIMEOUT.toMillis());
            return PushSendStatus.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.error("[기기] 푸시 발송 실패: 응답 대기 중단");
            return PushSendStatus.FAILED;
        }
    }

    private PushSendStatus toStatus(FirebaseMessagingException e) {
        // 앱 삭제·토큰 만료는 서버 문제가 아니다 — 기기를 끄는 쪽(유스케이스)이 남긴다
        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
            return PushSendStatus.UNREGISTERED;
        }
        // 원인 예외에 FCM 응답 본문이 담겨 온다 — 예외 객체 대신 직접 원인의 종류만 남긴다
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        log.error("[기기] 푸시 발송 실패: FCM 오류 - errorCode={}, messagingErrorCode={}, cause={}",
                e.getErrorCode(), e.getMessagingErrorCode(), cause.getClass().getSimpleName());
        return PushSendStatus.FAILED;
    }

    // 안드로이드는 앱이 백그라운드여도 바로 띄우게 HIGH, iOS는 소리가 기본으로 꺼져 있어 켠다
    private Message toMessage(PushToken pushToken, PushMessage message) {
        return Message.builder()
                .setToken(pushToken.value())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound(DEFAULT_SOUND).build())
                        .build())
                .build();
    }
}
