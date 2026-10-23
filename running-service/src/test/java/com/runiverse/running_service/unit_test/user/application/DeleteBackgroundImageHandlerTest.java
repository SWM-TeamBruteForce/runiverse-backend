package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageHandler;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.application.user.port.out.ClearBackgroundImagePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("배경 이미지 삭제 단위 테스트")
public class DeleteBackgroundImageHandlerTest {

    @Mock
    private ClearBackgroundImagePort clearBackgroundImagePort;

    @InjectMocks
    private DeleteBackgroundImageHandler handler;

    @Test
    @DisplayName("요청한 사용자의 배경 이미지 연결을 끊는다")
    void clearsBackgroundImageOfRequestedUser() {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        // when
        handler.handle(new DeleteBackgroundImageCommand(userId));

        // then -> S3 삭제 없이 DB의 key만 끊는다
        verify(clearBackgroundImagePort).clearBackgroundImage(new UserId(userId));
    }

    @Test
    @DisplayName("이미지가 없는 사용자가 삭제해도 예외 없이 끝난다")
    void succeedsWhenUserHasNoBackgroundImage() {
        // given -> 포트는 이미 비어 있어도 그대로 성공한다. 삭제는 멱등이다
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        // when & then
        assertThatCode(() -> handler.handle(new DeleteBackgroundImageCommand(userId)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("사용자가 없으면 포트의 예외를 그대로 전달한다")
    void propagatesUserNotFound() {
        // given
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        doThrow(new UserNotFoundException())
                .when(clearBackgroundImagePort).clearBackgroundImage(new UserId(unknownUserId));

        // when & then -> 핸들러가 삼키지 않는다
        assertThatThrownBy(() -> handler.handle(new DeleteBackgroundImageCommand(unknownUserId)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
