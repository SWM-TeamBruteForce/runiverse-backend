package com.runiverse.running_service.unit_test.user.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.application.user.port.out.LoadNicknamePort;
import com.runiverse.running_service.application.user.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileHandler;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import com.runiverse.running_service.domain.user.vo.Nickname;
import com.runiverse.running_service.domain.user.vo.ProfileVisibility;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 요약 조회 단위 테스트")
public class GetUserProfileHandlerTest {

    // PasswordHash VO가 Argon2id 형식만 허용하므로 형식에 맞는 값을 쓴다
    private static final String PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$aGFzaHZhbHVl";
    private static final String NICKNAME = "동완러너";
    private static final String INTRODUCTION = "즐겁게 달려요";
    private static final String VIEW_URL =
            "https://runiverse-test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/photo.jpg?X-Amz-Signature=test";

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private LoadNicknamePort loadNicknamePort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    @InjectMocks
    private GetUserProfileHandler handler;

    private static User userWith(UUID userId, String profileImageKey, String introduction) {
        return new User(userId, "runner@runiverse.com", PASSWORD_HASH, true,
                profileImageKey, ProfileVisibility.PUBLIC, introduction);
    }

    private static String keyOf(UUID userId) {
        return "profiles/" + userId + "/019ffa54-917f-7477-9482-5792597ef3b0.jpg";
    }

    @Test
    @DisplayName("타인의 프로필은 isMe가 false이고 친구 상태가 담긴다")
    void returnsOtherUserProfile() {
        // given
        UUID viewerId = UuidCreator.getTimeOrderedEpoch();
        UUID targetUserId = UuidCreator.getTimeOrderedEpoch();
        String key = keyOf(targetUserId);
        when(loadUserByIdPort.loadById(new UserId(targetUserId)))
                .thenReturn(Optional.of(userWith(targetUserId, key, INTRODUCTION)));
        when(loadNicknamePort.loadNickname(new UserId(targetUserId)))
                .thenReturn(Optional.of(new Nickname(NICKNAME)));
        when(generateViewUrlPort.generate(key)).thenReturn(VIEW_URL);

        // when
        GetUserProfileResult result = handler.handle(new GetUserProfileQuery(viewerId, targetUserId));

        // then -> 타인이면 친구 요청 버튼을 가르는 값이 필요하다
        assertThat(result.userId()).isEqualTo(targetUserId);
        assertThat(result.isMe()).isFalse();
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(result.profileImageUrl()).isEqualTo(VIEW_URL);
        assertThat(result.introduction()).isEqualTo(INTRODUCTION);
        assertThat(result.friendStatus()).isNotNull();
    }

    @Test
    @DisplayName("본인의 프로필은 isMe가 true이고 친구 상태가 없다")
    void returnsOwnProfileWithoutFriendStatus() {
        // given -> 조회하는 쪽과 대상이 같다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(userId)))
                .thenReturn(Optional.of(userWith(userId, null, INTRODUCTION)));
        when(loadNicknamePort.loadNickname(new UserId(userId)))
                .thenReturn(Optional.of(new Nickname(NICKNAME)));

        // when
        GetUserProfileResult result = handler.handle(new GetUserProfileQuery(userId, userId));

        // then -> 본인에게는 친구 관계가 없어 NONE이 아니라 null이다
        assertThat(result.isMe()).isTrue();
        assertThat(result.friendStatus()).isNull();
    }

    @Test
    @DisplayName("프로필 사진이 없으면 URL 없이 반환하고 발급을 시도하지 않는다")
    void returnsNullUrlWhenImageIsNotRegistered() {
        // given
        UUID viewerId = UuidCreator.getTimeOrderedEpoch();
        UUID targetUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(targetUserId)))
                .thenReturn(Optional.of(userWith(targetUserId, null, INTRODUCTION)));
        when(loadNicknamePort.loadNickname(new UserId(targetUserId)))
                .thenReturn(Optional.of(new Nickname(NICKNAME)));

        // when
        GetUserProfileResult result = handler.handle(new GetUserProfileQuery(viewerId, targetUserId));

        // then -> 사진이 없는 건 정상 상태라 예외가 아니다
        assertThat(result.profileImageUrl()).isNull();
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("소개글이 없으면 빈 문자열이 아니라 null로 답한다")
    void returnsNullIntroductionWhenNeverWritten() {
        // given -> users.introduction이 null이면 어댑터가 ""로 바꿔 올린다
        UUID viewerId = UuidCreator.getTimeOrderedEpoch();
        UUID targetUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(targetUserId)))
                .thenReturn(Optional.of(userWith(targetUserId, null, "")));
        when(loadNicknamePort.loadNickname(new UserId(targetUserId)))
                .thenReturn(Optional.of(new Nickname(NICKNAME)));

        // when
        GetUserProfileResult result = handler.handle(new GetUserProfileQuery(viewerId, targetUserId));

        // then -> 조회 응답의 "값 없음"은 profileImageUrl과 같이 null이다
        assertThat(result.introduction()).isNull();
    }

    @Test
    @DisplayName("온보딩 전이면 닉네임 없이 반환한다")
    void returnsNullNicknameBeforeOnboarding() {
        // given -> 닉네임은 온보딩에서 처음 생긴다
        UUID viewerId = UuidCreator.getTimeOrderedEpoch();
        UUID targetUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(targetUserId)))
                .thenReturn(Optional.of(userWith(targetUserId, null, INTRODUCTION)));
        when(loadNicknamePort.loadNickname(new UserId(targetUserId))).thenReturn(Optional.empty());

        // when
        GetUserProfileResult result = handler.handle(new GetUserProfileQuery(viewerId, targetUserId));

        // then
        assertThat(result.nickname()).isNull();
    }

    @Test
    @DisplayName("대상 사용자가 없으면 닉네임을 조회하지 않고 예외를 던진다")
    void throwsWhenTargetUserNotFound() {
        // given -> 탈퇴한 사용자도 같은 경로로 걸러진다
        UUID viewerId = UuidCreator.getTimeOrderedEpoch();
        UUID unknownUserId = UuidCreator.getTimeOrderedEpoch();
        when(loadUserByIdPort.loadById(new UserId(unknownUserId))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetUserProfileQuery(viewerId, unknownUserId)))
                .isInstanceOf(ProfileNotFoundException.class);
        verifyNoInteractions(loadNicknamePort, generateViewUrlPort);
    }
}
