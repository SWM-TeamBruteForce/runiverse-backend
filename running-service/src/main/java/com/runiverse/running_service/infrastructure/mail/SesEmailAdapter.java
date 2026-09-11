package com.runiverse.running_service.infrastructure.mail;

import com.runiverse.running_service.application.auth.exception.EmailSendFailedException;
import com.runiverse.running_service.application.auth.port.out.SendEmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
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
        } catch (SdkException e) {
            log.warn("SES 발송 실패 to={}", to, e);
            throw new EmailSendFailedException();
        }
    }

    private Content content(String data) {
        return Content.builder().data(data).charset(CHARSET).build();
    }
}
