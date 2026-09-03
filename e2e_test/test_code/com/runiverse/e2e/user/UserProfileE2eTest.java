package com.runiverse.e2e.user;

import com.runiverse.e2e.E2eTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 사용자 프로필 E2E 테스트")
class UserProfileE2eTest extends E2eTestSupport {

    @Test
    @DisplayName("온보딩한 값이 내 정보·프로필 조회에 그대로 보인다")
    void onboardedValuesAreVisible() {
        // given
        TestUser user = signUpAndOnboard();
        // when
        Response me = get("/users/me", user.accessToken());
        Response profile = get("/users/me/profile", user.accessToken());
        // then
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.text("nickname")).isEqualTo(user.nickname());
        assertThat(me.bool("isOnboarded")).isTrue();
        assertThat(profile.status()).isEqualTo(200);
        assertThat(profile.text("gender")).isEqualTo("MALE");
        assertThat(profile.text("birthday")).isEqualTo("1998-03-21");
        // 본인 프로필 요약은 isMe가 켜져서 온다
        Response summary = get("/users/" + user.userId(), user.accessToken());
        assertThat(summary.status()).isEqualTo(200);
        assertThat(summary.bool("isMe")).isTrue();
        assertThat(summary.text("nickname")).isEqualTo(user.nickname());
    }

    @Test
    @DisplayName("프로필을 부분 수정하면 보낸 값만 바뀌고 나머지는 유지된다")
    void profileIsPartiallyUpdated() {
        // given
        TestUser user = signUpAndOnboard();
        // when - 소개글과 몸무게만 보낸다
        Response updated = patch("/users/me/profile", Map.of(
                "introduction", "달리는 사람",
                "weightKg", new BigDecimal("70.0")
        ), user.accessToken());
        // then
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.text("introduction")).isEqualTo("달리는 사람");
        // 보내지 않은 성별·생일은 온보딩 값 그대로다
        Response profile = get("/users/me/profile", user.accessToken());
        assertThat(profile.text("gender")).isEqualTo("MALE");
        assertThat(profile.text("birthday")).isEqualTo("1998-03-21");
        assertThat(profile.text("introduction")).isEqualTo("달리는 사람");
    }

    @Test
    @DisplayName("닉네임을 바꾸면 이전 닉네임이 풀리고 새 닉네임은 중복으로 잡힌다")
    void nicknameIsChangedAndReserved() {
        // given
        TestUser user = signUpAndOnboard();
        String newNickname = uniqueNickname();
        // 바꾸기 전에는 아직 아무도 안 쓰는 이름이다
        assertThat(post("/users/nickname/availability", Map.of("nickname", newNickname))
                .bool("available")).isTrue();
        // when
        Response changed = patch("/users/me/nickname",
                Map.of("nickname", newNickname), user.accessToken());
        // then
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.text("nickname")).isEqualTo(newNickname);
        assertThat(get("/users/me", user.accessToken()).text("nickname")).isEqualTo(newNickname);
        // 이제는 선점된 이름이고, 놓아준 이름은 다시 쓸 수 있다
        assertThat(post("/users/nickname/availability", Map.of("nickname", newNickname))
                .bool("available")).isFalse();
        assertThat(post("/users/nickname/availability", Map.of("nickname", user.nickname()))
                .bool("available")).isTrue();
    }

    @Test
    @DisplayName("이미 쓰이는 닉네임으로 바꾸려 하면 409로 막힌다")
    void duplicatedNicknameIsRejected() {
        // given
        TestUser owner = signUpAndOnboard();
        TestUser challenger = signUpAndOnboard();
        // when
        Response response = patch("/users/me/nickname",
                Map.of("nickname", owner.nickname()), challenger.accessToken());
        // then
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("code")).isEqualTo("NICKNAME_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 새 비밀번호로만 로그인된다")
    void passwordIsChanged() {
        // given
        TestUser user = signUpAndOnboard();
        String newPassword = "Runiverse1!";
        // 현재 비밀번호가 틀리면 401로 먼저 걸린다
        assertThat(patch("/users/me/password", Map.of(
                "currentPassword", "Wrong123!",
                "newPassword", newPassword
        ), user.accessToken()).status()).isEqualTo(401);
        // when
        Response changed = patch("/users/me/password", Map.of(
                "currentPassword", user.password(),
                "newPassword", newPassword
        ), user.accessToken());
        // then - 토큰을 무효화하지는 않으므로 로그인 결과로만 판정한다
        assertThat(changed.status()).isEqualTo(204);
        assertThat(post("/auth/login",
                Map.of("email", user.email(), "password", newPassword)).status()).isEqualTo(200);
        assertThat(post("/auth/login",
                Map.of("email", user.email(), "password", user.password())).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("프로필 사진 업로드 URL이 발급되고, 사진 없이도 삭제는 통과한다")
    void profileImageUploadUrlIsIssued() {
        // given
        TestUser user = signUpAndOnboard();
        // when - presign은 로컬 서명이라 S3에 나가지 않는다
        Response issued = post("/users/me/profile-image/presigned-url", Map.of(
                "mimeType", "image/png",
                "fileSizeBytes", 1024
        ), user.accessToken());
        // then
        assertThat(issued.status()).isEqualTo(200);
        assertThat(issued.text("profileImageKey")).isNotBlank();
        assertThat(issued.text("uploadUrl")).startsWith("http");
        // 사진이 없어도 삭제는 멱등하게 통과한다 — S3를 부르지 않고 DB만 비운다
        assertThat(delete("/users/me/profile-image", user.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("업로드하지 않은 키로는 프로필 사진을 반영할 수 없다")
    void unuploadedProfileImageKeyIsRejected() {
        // given - URL만 받고 실제 업로드는 하지 않는다
        TestUser user = signUpAndOnboard();
        String key = post("/users/me/profile-image/presigned-url", Map.of(
                "mimeType", "image/png",
                "fileSizeBytes", 1024
        ), user.accessToken()).text("profileImageKey");
        // when - 서버가 S3에 실제로 객체가 있는지 확인한다
        Response response = patch("/users/me/profile-image",
                Map.of("profileImageKey", key), user.accessToken());
        // then - 발급만으로는 사진이 생기지 않는다
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("PROFILE_IMAGE_NOT_UPLOADED");
        // 반영이 막혔으므로 내 정보에도 사진이 붙지 않는다
        assertThat(get("/users/" + user.userId(), user.accessToken())
                .text("profileImageUrl")).isNull();
    }

    @Test
    @DisplayName("허용하지 않는 형식은 업로드 URL 발급 단계에서 400으로 걸린다")
    void unsupportedImageTypeIsRejected() {
        // given
        TestUser user = signUpAndOnboard();
        // when
        Response response = post("/users/me/profile-image/presigned-url", Map.of(
                "mimeType", "image/gif",
                "fileSizeBytes", 1024
        ), user.accessToken());
        // then
        assertThat(response.status()).isEqualTo(400);
    }
}
