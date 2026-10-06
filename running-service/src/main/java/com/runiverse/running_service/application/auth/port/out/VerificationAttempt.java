package com.runiverse.running_service.application.auth.port.out;

public record VerificationAttempt(
        Status status,
        String hashedCode
) {

    // NOT_FOUND -> 인증코드를 찾울 수 있다.
    // EXHAUSTED -> 인증 횟수 초과
    // AVAILABLE -> 이용 가능
    public enum Status {NOT_FOUND, EXHAUSTED, AVAILABLE}
}
