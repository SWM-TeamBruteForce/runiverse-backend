package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.SendEmailPort;

import java.util.ArrayList;
import java.util.List;

public class FakeEmailSender implements SendEmailPort {

    public record SentEmail(String to, String subject, String body) {

    }

    private final List<SentEmail> sent = new ArrayList<>();
    private RuntimeException failure;

    // 발송 실패 시 롤백이 도는지 보기 위한 스위치
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (failure != null) {
            throw failure;
        }
        sent.add(new SentEmail(to, subject, body));
    }

    // 아래는 검증 전용
    public int size() {
        return sent.size();
    }

    public boolean isEmpty() {
        return sent.isEmpty();
    }

    public SentEmail last() {
        return sent.get(sent.size() - 1);
    }
}
