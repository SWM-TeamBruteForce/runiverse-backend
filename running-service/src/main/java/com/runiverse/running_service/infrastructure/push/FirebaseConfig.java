package com.runiverse.running_service.infrastructure.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
@Profile("!local")
public class FirebaseConfig {

    // 커밋 뒤 요청 스레드에서 보내므로 응답이 이만큼 늦어질 수 있다
    private static final int CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final int READ_TIMEOUT_MILLIS = 3_000;

    // 앱은 Firebase의 static 목록에 등록된다. AWS 클라이언트와 달리 종료 메서드가 close가 아니라
    // 스프링이 알아서 부르지 않는다 — 컨텍스트가 닫힐 때 목록에서 지우도록 직접 지정한다
    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(FcmProperties properties) throws IOException {
        byte[] serviceAccount = Base64.getDecoder().decode(properties.serviceAccountBase64());
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccount)))
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                .setReadTimeout(READ_TIMEOUT_MILLIS)
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
