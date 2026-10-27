package com.runiverse.running_service.infrastructure.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushSendResult;
import com.runiverse.running_service.application.device.port.out.SendPushPort;
import com.runiverse.running_service.domain.device.vo.PushToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 안드로이드·iOS 모두 FCM 하나로 보낸다 — iOS는 FCM이 APNs로 넘긴다
@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class FcmPushClient implements SendPushPort {

    private static final String DEFAULT_SOUND = "default";

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public PushSendResult send(PushToken pushToken, PushMessage message) {
        try {
            firebaseMessaging.send(toMessage(pushToken, message));
            return PushSendResult.SENT;
        } catch (FirebaseMessagingException e) {
            // 앱 삭제·토큰 만료는 서버 문제가 아니다 — 기기를 끄는 쪽(유스케이스)이 남긴다
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                return PushSendResult.UNREGISTERED;
            }
            // 원인 예외에 FCM 응답 본문이 담겨 온다 — 예외 객체 대신 종류만 남긴다
            log.error("[기기] 푸시 발송 실패: FCM 오류 - errorCode={}, messagingErrorCode={}, cause={}",
                    e.getErrorCode(), e.getMessagingErrorCode(),
                    e.getCause() == null ? null : e.getCause().getClass().getSimpleName());
            return PushSendResult.FAILED;
        }
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
