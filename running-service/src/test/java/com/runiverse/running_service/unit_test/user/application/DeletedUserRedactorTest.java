package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.accountdeletion.DeletedUserRedactor;
import com.runiverse.running_service.application.user.port.out.DeleteImagesPort;
import com.runiverse.running_service.application.user.port.out.RedactDeletedUserPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("탈퇴 기록 한 건 신원 정보 제거 단위 테스트")
class DeletedUserRedactorTest {

    @Mock
    private DeleteImagesPort deleteImagesPort;
    @Mock
    private RedactDeletedUserPort redactDeletedUserPort;

    @InjectMocks
    private DeletedUserRedactor redactor;

    @Test
    @DisplayName("프로필·배경 사진을 모두 지운 뒤에 기록을 비운다")
    void deletesImagesBeforeRedactingRecord() {
        // given
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());

        // when
        redactor.redact(userId);

        // then -> 기록을 먼저 비우면 다음 실행에서 걸리지 않아 사진만 남는다
        InOrder order = inOrder(deleteImagesPort, redactDeletedUserPort);
        order.verify(deleteImagesPort, times(2)).deleteAllByPrefix(anyString());
        order.verify(redactDeletedUserPort).redact(userId);
    }

    @Test
    @DisplayName("해당 유저의 프로필·배경 프리픽스만 넘긴다")
    void passesOnlyTheOwnPrefixes() {
        // given
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());

        // when
        redactor.redact(userId);

        // then -> 프리픽스가 좁아지거나 넓어지면 남의 사진까지 지운다
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(deleteImagesPort, times(2)).deleteAllByPrefix(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrder(
                profilePrefixOf(userId),
                backgroundPrefixOf(userId));
    }

    @Test
    @DisplayName("프로필 사진 삭제가 실패하면 기록을 비우지 않는다")
    void keepsRecordWhenProfileImageDeletionFails() {
        // given -> 기록이 남아야 다음 실행이 이 유저를 다시 집어 간다
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        doThrow(new IllegalStateException("S3 삭제 실패"))
                .when(deleteImagesPort).deleteAllByPrefix(profilePrefixOf(userId));

        // when & then
        assertThatThrownBy(() -> redactor.redact(userId))
                .isInstanceOf(IllegalStateException.class);
        verify(redactDeletedUserPort, never()).redact(userId);
    }

    @Test
    @DisplayName("배경 사진 삭제가 실패하면 기록을 비우지 않는다")
    void keepsRecordWhenBackgroundImageDeletionFails() {
        // given -> 프로필만 지워진 채로 기록을 비우면 배경이 영영 남는다
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        doNothing().when(deleteImagesPort).deleteAllByPrefix(profilePrefixOf(userId));
        doThrow(new IllegalStateException("S3 삭제 실패"))
                .when(deleteImagesPort).deleteAllByPrefix(backgroundPrefixOf(userId));

        // when & then
        assertThatThrownBy(() -> redactor.redact(userId))
                .isInstanceOf(IllegalStateException.class);
        verify(redactDeletedUserPort, never()).redact(userId);
    }

    private String profilePrefixOf(UserId userId) {
        return "profiles/" + userId.value() + "/";
    }

    private String backgroundPrefixOf(UserId userId) {
        return "backgrounds/" + userId.value() + "/";
    }
}
