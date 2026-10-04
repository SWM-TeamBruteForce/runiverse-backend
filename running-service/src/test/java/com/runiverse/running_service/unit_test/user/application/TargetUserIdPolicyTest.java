package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.common.TargetUserIdPolicy;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("대상 userId 정책 단위 테스트")
public class TargetUserIdPolicyTest {

    private static final UUID V7 = UUID.fromString("019ff918-a5ac-75b6-b20b-350454888411");
    private static final UUID V4 = UUID.fromString("00000000-0000-4000-8000-000000000000");

    @Test
    @DisplayName("서버가 발급하는 v7이면 그대로 도메인 값이 된다")
    void resolvesIssuedVersion() {
        // when & then
        assertThat(TargetUserIdPolicy.resolve(V7)).isEqualTo(new UserId(V7));
    }

    @Test
    @DisplayName("v7이 아니면 형식 오류가 아니라 없는 사용자다")
    void treatsOtherVersionAsNotFound() {
        // when & then -> 경로 ID는 타입만 검사하고, 나머지는 없는 리소스로 답한다
        assertThatThrownBy(() -> TargetUserIdPolicy.resolve(V4))
                .isInstanceOf(ProfileNotFoundException.class);
    }
}
