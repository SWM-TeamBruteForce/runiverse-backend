package com.runiverse.running_service.unit_test.infrastructure.mail;

import com.runiverse.running_service.application.auth.exception.EmailSendFailedException;
import com.runiverse.running_service.infrastructure.mail.SesEmailAdapter;
import com.runiverse.running_service.infrastructure.mail.SesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SesEmailAdapterTest {

    private static final String FROM = "no-reply@runiverse.com";
    private static final String FROM_NAME = "Runiverse";
    private static final String TO = "runner@runiverse.com";
    private static final String SUBJECT = "[Runiverse] 이메일 인증 코드";
    private static final String BODY = "인증 코드는 123456 입니다.";

    @Mock
    private SesV2Client sesV2Client;

    private SesEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        // 자격증명은 비워 둔다. 실제 배포에서는 IAM Role을 쓴다
        adapter = new SesEmailAdapter(sesV2Client, new SesProperties("ap-northeast-2", FROM, FROM_NAME, null, null));
    }

    @Test
    @DisplayName("보내는 주소, 받는 주소, 제목, 본문을 UTF-8로 담아 SES에 넘긴다")
    void sendBuildsRequest() {
        // given
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        // when
        adapter.send(TO, SUBJECT, BODY);

        // then
        verify(sesV2Client).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();
        assertThat(request.destination().toAddresses()).containsExactly(TO);
        // 한글 제목과 본문이 깨지지 않으려면 charset이 반드시 붙어야 한다
        assertThat(request.content().simple().subject().data()).isEqualTo(SUBJECT);
        assertThat(request.content().simple().subject().charset()).isEqualTo("UTF-8");
        assertThat(request.content().simple().body().text().data()).isEqualTo(BODY);
        assertThat(request.content().simple().body().text().charset()).isEqualTo("UTF-8");
    }

    @Test
    @DisplayName("발신자를 이름과 주소를 묶은 형식으로 넘긴다")
    void sendCombinesDisplayNameWithAddress() {
        // given
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        // when
        adapter.send(TO, SUBJECT, BODY);

        // then -> 상수를 조합해 검증하면 구현과 같은 식이 되어 형식이 틀려도 통과한다
        verify(sesV2Client).sendEmail(captor.capture());
        assertThat(captor.getValue().fromEmailAddress()).isEqualTo("Runiverse <no-reply@runiverse.com>");
    }

    @Test
    @DisplayName("SES 호출이 실패하면 EmailSendFailedException으로 바꾼다")
    void sendWrapsSdkException() {
        // given - SDK 예외가 그대로 올라가면 발송 핸들러의 롤백 의미가 흐려진다
        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SdkException.builder().message("ses down").build());

        // when & then
        assertThatThrownBy(() -> adapter.send(TO, SUBJECT, BODY))
                .isInstanceOf(EmailSendFailedException.class);
    }
}
