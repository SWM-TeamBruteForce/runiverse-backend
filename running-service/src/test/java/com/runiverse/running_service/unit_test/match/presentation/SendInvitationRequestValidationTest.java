package com.runiverse.running_service.unit_test.match.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.presentation.match.request.SendInvitationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// DTO는 대상이 있는지만 본다. 친구 여부·지금 상태·보낸 횟수는 SendInvitationHandler가 본다
@DisplayName("친구 초대 요청 DTO 검증 단위 테스트")
class SendInvitationRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private static List<String> messagesOf(SendInvitationRequest request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }

    @Test
    @DisplayName("대상이 있으면 통과한다")
    void presentInviteePasses() {
        // when & then
        assertThat(messagesOf(new SendInvitationRequest(UuidCreator.getTimeOrderedEpoch()))).isEmpty();
    }

    @Test
    @DisplayName("대상이 빠지면 필수 위반이다")
    void missingInviteeIsRejected() {
        // when & then
        assertThat(messagesOf(new SendInvitationRequest(null))).containsExactly("초대할 친구는 필수입니다.");
    }
}
