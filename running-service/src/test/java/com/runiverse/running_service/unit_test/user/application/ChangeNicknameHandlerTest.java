package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameCommand;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameHandler;
import com.runiverse.running_service.application.user.command.nickname.ChangeNicknameResult;
import com.runiverse.running_service.application.user.exception.NicknameAlreadyExistsException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.application.user.port.out.CheckNicknameDuplicatePort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.UpdateNicknamePort;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameFormatException;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.exception.NicknameRequiredException;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("닉네임 변경 단위 테스트")
public class ChangeNicknameHandlerTest {

    private static final String CURRENT_NICKNAME = "러너킴";
    private static final String NEW_NICKNAME = "완두콩";

    @Mock
    private LoadNicknamePort loadNicknamePort;

    @Mock
    private CheckNicknameDuplicatePort checkNicknameDuplicatePort;

    @Mock
    private UpdateNicknamePort updateNicknamePort;

    @InjectMocks
    private ChangeNicknameHandler handler;

    @Test
    @DisplayName("중복되지 않은 닉네임으로 바꾸면 갱신하고 새 닉네임을 반환한다")
    void changeNicknameSuccess() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadNicknamePort.loadNickname(new UserId(userId)))
                .willReturn(Optional.of(new Nickname(CURRENT_NICKNAME)));
        given(checkNicknameDuplicatePort.existsByNickname(new Nickname(NEW_NICKNAME)))
                .willReturn(false);

        // when
        ChangeNicknameResult result = handler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME));

        // then
        assertThat(result.nickname()).isEqualTo(NEW_NICKNAME);
        verify(updateNicknamePort).updateNickname(new UserId(userId), new Nickname(NEW_NICKNAME));
    }

    @Test
    @DisplayName("온보딩을 하지 않았으면 중복 검사까지 가지 않고 막는다")
    void onboardingNotCompletedIsRejected() {
        // given -> 닉네임은 온보딩에서 처음 생기므로 행 자체가 없다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadNicknamePort.loadNickname(new UserId(userId))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME)))
                .isInstanceOf(OnboardingNotCompletedException.class);
        verifyNoInteractions(checkNicknameDuplicatePort, updateNicknamePort);
    }

    @Test
    @DisplayName("지금 쓰는 닉네임과 같으면 중복 검사도 갱신도 하지 않는다")
    void sameNicknameIsNoOp() {
        // given -> 자기 닉네임은 이미 자기가 점유 중이라 중복 검사를 태우면 스스로에게 막힌다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadNicknamePort.loadNickname(new UserId(userId)))
                .willReturn(Optional.of(new Nickname(CURRENT_NICKNAME)));

        // when
        ChangeNicknameResult result = handler.handle(new ChangeNicknameCommand(userId, CURRENT_NICKNAME));

        // then
        assertThat(result.nickname()).isEqualTo(CURRENT_NICKNAME);
        verifyNoInteractions(checkNicknameDuplicatePort, updateNicknamePort);
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 닉네임은 VO가 다듬어 같은 값으로 보고 갱신하지 않는다")
    void nicknameIsTrimmedBeforeComparison() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadNicknamePort.loadNickname(new UserId(userId)))
                .willReturn(Optional.of(new Nickname(CURRENT_NICKNAME)));

        // when
        ChangeNicknameResult result = handler.handle(
                new ChangeNicknameCommand(userId, "  " + CURRENT_NICKNAME + "  "));

        // then
        assertThat(result.nickname()).isEqualTo(CURRENT_NICKNAME);
        verifyNoInteractions(checkNicknameDuplicatePort, updateNicknamePort);
    }

    @Test
    @DisplayName("남이 쓰고 있는 닉네임이면 갱신하지 않고 막는다")
    void duplicateNicknameIsRejected() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(loadNicknamePort.loadNickname(new UserId(userId)))
                .willReturn(Optional.of(new Nickname(CURRENT_NICKNAME)));
        given(checkNicknameDuplicatePort.existsByNickname(new Nickname(NEW_NICKNAME)))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> handler.handle(new ChangeNicknameCommand(userId, NEW_NICKNAME)))
                .isInstanceOf(NicknameAlreadyExistsException.class);
        verify(updateNicknamePort, never()).updateNickname(new UserId(userId), new Nickname(NEW_NICKNAME));
    }

    @Test
    @DisplayName("닉네임 형식이 어긋나면 포트를 부르기 전에 VO가 막는다")
    void invalidNicknameIsRejectedBeforeAnyPortCall() {
        // given -> presentation의 Bean Validation이 400으로 걸러도 VO가 마지막 방어선이다
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        // when & then
        assertThatThrownBy(() -> handler.handle(new ChangeNicknameCommand(userId, "가")))
                .isInstanceOf(InvalidNicknameLengthException.class);
        assertThatThrownBy(() -> handler.handle(new ChangeNicknameCommand(userId, "완두콩!")))
                .isInstanceOf(InvalidNicknameFormatException.class);
        assertThatThrownBy(() -> handler.handle(new ChangeNicknameCommand(userId, null)))
                .isInstanceOf(NicknameRequiredException.class);
        verifyNoInteractions(loadNicknamePort, checkNicknameDuplicatePort, updateNicknamePort);
    }
}
