package com.runiverse.running_service.infrastructure.mail;

import com.runiverse.running_service.application.auth.exception.EmailSendFailedException;
import com.runiverse.running_service.application.auth.port.out.SendEmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class SesEmailAdapter implements SendEmailPort {

    private static final String CHARSET = "UTF-8";
    private final SesV2Client sesV2Client;
    private final SesProperties properties;

    @Override
    public void send(String to, String subject, String body) {
        try {
            sesV2Client.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(properties.fromName() + " <" + properties.from() + ">")
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(content(subject))
                                    .body(Body.builder().text(content(body)).build())
                                    .build())
                            .build())
                    .build());
        } catch (AwsServiceException e) {
            // SES 오류 메시지에 수신 주소가 담겨 온다(샌드박스의 미인증 주소 거부 등) — 예외 대신 오류 코드만 남긴다
            log.error("[인증] 인증 메일 발송 실패: SES 응답 오류 - status={}, errorCode={}",
                    e.statusCode(), e.awsErrorDetails() == null ? "unknown" : e.awsErrorDetails().errorCode());
            throw new EmailSendFailedException();
        } catch (SdkClientException e) {
            // 요청이 SES에 닿기 전에 실패했다 — 메시지에 주소가 없어 스택째 남긴다
            log.error("[인증] 인증 메일 발송 실패: SES 통신 오류", e);
            throw new EmailSendFailedException();
        } catch (SdkException e) {
            // 위 둘로 갈리지 않는 SDK 예외 — 메시지에 무엇이 담길지 알 수 없어 종류만 남긴다
            log.error("[인증] 인증 메일 발송 실패: SES 처리 오류 - cause={}", e.getClass().getSimpleName());
            throw new EmailSendFailedException();
        }
    }

    private Content content(String data) {
        return Content.builder().data(data).charset(CHARSET).build();
    }
}
