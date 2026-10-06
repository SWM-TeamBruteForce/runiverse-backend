package com.runiverse.running_service.unit_test.user.application;

import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityHandler;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityQuery;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityResult;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameFormatException;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.exception.NicknameRequiredException;
import com.runiverse.running_service.domain.user.vo.Nickname;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("닉네임 사용 가능 여부 확인 단위 테스트")
public class CheckNicknameAvailabilityHandlerTest {

    private static final String NICKNAME = "완두콩";

    @Mock
    private CheckNicknameDuplicatePort checkNicknameDuplicatePort;

    @InjectMocks
    private CheckNicknameAvailabilityHandler handler;

    @Test
    @DisplayName("아무도 쓰지 않는 닉네임은 사용 가능으로 답한다")
    void unusedNicknameIsAvailable() {
        // given
        given(checkNicknameDuplicatePort.existsByNickname(new Nickname(NICKNAME))).willReturn(false);

        // when
        CheckNicknameAvailabilityResult result = handler.handle(
                new CheckNicknameAvailabilityQuery(NICKNAME));

        // then
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(result.available()).isTrue();
    }

    @Test
    @DisplayName("이미 쓰이는 닉네임은 사용 불가로 답한다")
    void takenNicknameIsNotAvailable() {
        // given -> existsByNickname은 "점유 중"이 true다. available과 뜻이 반대라 뒤집어 담아야 한다
        given(checkNicknameDuplicatePort.existsByNickname(new Nickname(NICKNAME))).willReturn(true);

        // when
        CheckNicknameAvailabilityResult result = handler.handle(
                new CheckNicknameAvailabilityQuery(NICKNAME));

        // then
        assertThat(result.available()).isFalse();
    }

    @Test
    @DisplayName("앞뒤 공백은 VO가 다듬은 뒤 조회하고 다듬은 값을 돌려준다")
    void nicknameIsTrimmedBeforeLookup() {
        // given -> 공백이 남은 채로 조회하면 점유 중인 닉네임도 사용 가능으로 나온다
        given(checkNicknameDuplicatePort.existsByNickname(new Nickname(NICKNAME))).willReturn(true);

        // when
        CheckNicknameAvailabilityResult result = handler.handle(
                new CheckNicknameAvailabilityQuery("  " + NICKNAME + "  "));

        // then -> 클라이언트가 입력창에 그대로 반영할 수 있도록 다듬은 값을 준다
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(result.available()).isFalse();
        verify(checkNicknameDuplicatePort).existsByNickname(new Nickname(NICKNAME));
    }

    @Test
    @DisplayName("닉네임 형식이 어긋나면 조회하기 전에 VO가 막는다")
    void invalidNicknameIsRejectedBeforeLookup() {
        // given -> presentation의 Bean Validation이 400으로 걸러도 VO가 마지막 방어선이다

        // when & then
        assertThatThrownBy(() -> handler.handle(new CheckNicknameAvailabilityQuery("가")))
                .isInstanceOf(InvalidNicknameLengthException.class);
        assertThatThrownBy(() -> handler.handle(new CheckNicknameAvailabilityQuery("완두콩!")))
                .isInstanceOf(InvalidNicknameFormatException.class);
        assertThatThrownBy(() -> handler.handle(new CheckNicknameAvailabilityQuery(null)))
                .isInstanceOf(NicknameRequiredException.class);
        verifyNoInteractions(checkNicknameDuplicatePort);
    }
}
