package com.runiverse.running_service.unit_test.user.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.ChangeBackgroundImageResult;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.backgroundimage.CreateBackgroundImageUploadUrlResult;
import com.runiverse.running_service.application.user.command.backgroundimage.DeleteBackgroundImageCommand;
import com.runiverse.running_service.application.user.exception.BackgroundImageNotUploadedException;
import com.runiverse.running_service.application.user.exception.InvalidBackgroundImageException;
import com.runiverse.running_service.application.user.port.in.ChangeBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.in.CreateBackgroundImageUploadUrlUsecase;
import com.runiverse.running_service.application.user.port.in.DeleteBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetBackgroundImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetUserProfileUsecase;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlQuery;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlResult;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.user.controller.UserController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 경로의 userId는 컨트롤러가 거른다 — 유스케이스로 넘어가면 VO 검증에서 500이 된다.
// 주제와 무관한 유스케이스는 생성자에 null로 들어간다
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 컨트롤러 단위 테스트")
class UserControllerTest {

    private static final UUID VIEWER_ID = UuidCreator.getTimeOrderedEpoch();
    // 형식은 UUID지만 서버가 발급하지 않는 버전이다
    private static final String NON_V7_USER_ID = "00000000-0000-4000-8000-000000000000";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private GetUserProfileUsecase getUserProfileUsecase;

    @Mock
    private GetProfileImageUsecase getProfileImageUsecase;

    @Mock
    private CreateBackgroundImageUploadUrlUsecase createBackgroundImageUploadUrlUsecase;

    @Mock
    private ChangeBackgroundImageUsecase changeBackgroundImageUsecase;

    @Mock
    private GetBackgroundImageUsecase getBackgroundImageUsecase;

    @Mock
    private DeleteBackgroundImageUsecase deleteBackgroundImageUsecase;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(VIEWER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("프로필 사진 조회: 경로의 v7 userId를 그대로 유스케이스에 넘긴다")
    void 프로필_사진_조회는_경로의_userId를_넘긴다() throws Exception {
        // given -> userId가 다르게 바인딩되면 이 스텁이 맞지 않아 빈 응답이 된다
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(getProfileImageUsecase.handle(new GetProfileImageUrlQuery(userId)))
                .willReturn(new GetProfileImageUrlResult(userId, "https://cdn.example.com/profile.jpg"));

        // when
        MvcResult result = mockMvc.perform(get("/users/" + userId + "/profile-image")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsEntry("profileImageUrl", "https://cdn.example.com/profile.jpg");
    }

    @Test
    @DisplayName("프로필 조회: v7이 아닌 userId는 400이고 조회하지 않는다")
    void 프로필_조회는_v7이_아닌_userId에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/" + NON_V7_USER_ID)).andReturn();

        // then
        assertInvalidRequest(result);
        then(getUserProfileUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 조회: 자리 수가 모자란 userId는 400이다 -> UUID 변환은 이 모양도 받아 준다")
    void 프로필_조회는_짧은_userId에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/1-1-1-1-1")).andReturn();

        // then
        assertInvalidRequest(result);
        then(getUserProfileUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 조회: UUID가 아닌 userId도 400 기본 문구다")
    void 프로필_조회는_UUID가_아닌_userId에_기본_문구다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/abc")).andReturn();

        // then
        assertInvalidRequest(result);
        then(getUserProfileUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 사진 조회: v7이 아닌 userId는 400이고 조회하지 않는다")
    void 프로필_사진_조회는_v7이_아닌_userId에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/" + NON_V7_USER_ID + "/profile-image")).andReturn();

        // then
        assertInvalidRequest(result);
        then(getProfileImageUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배경 업로드 URL 발급: 토큰의 userId와 본문을 넘기고 key와 URL을 돌려준다")
    void 배경_업로드_URL_발급은_토큰과_본문을_넘긴다() throws Exception {
        // given
        String key = "backgrounds/" + VIEWER_ID + "/bg.jpg";
        given(createBackgroundImageUploadUrlUsecase.handle(
                new CreateBackgroundImageUploadUrlCommand(VIEWER_ID, "image/jpeg", 204800L)))
                .willReturn(new CreateBackgroundImageUploadUrlResult(key, "https://upload.example.com"));

        // when
        MvcResult result = mockMvc.perform(post("/users/me/background-image/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mimeType\":\"image/jpeg\",\"fileSizeBytes\":204800}"))
                .andReturn();

        // then -> 응답 필드 이름이 API 계약이다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result))
                .containsEntry("backgroundImageKey", key)
                .containsEntry("uploadUrl", "https://upload.example.com");
    }

    @Test
    @DisplayName("배경 업로드 URL 발급: 허용하지 않는 형식은 400이고 발급하지 않는다")
    void 배경_업로드_URL_발급은_gif에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/users/me/background-image/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mimeType\":\"image/gif\",\"fileSizeBytes\":204800}"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다.");
        then(createBackgroundImageUploadUrlUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배경 업로드 URL 발급: 10MB를 넘으면 400이고 발급하지 않는다")
    void 배경_업로드_URL_발급은_10MB_초과에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/users/me/background-image/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mimeType\":\"image/png\",\"fileSizeBytes\":10485761}"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("message", "이미지는 10MB 이하만 업로드할 수 있습니다.");
        then(createBackgroundImageUploadUrlUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배경 반영: 토큰의 userId와 key를 넘기고 보낸 key만 돌려준다")
    void 배경_반영은_토큰과_key를_넘긴다() throws Exception {
        // given
        String key = "backgrounds/" + VIEWER_ID + "/bg.jpg";
        given(changeBackgroundImageUsecase.handle(new ChangeBackgroundImageCommand(VIEWER_ID, key)))
                .willReturn(new ChangeBackgroundImageResult(VIEWER_ID, key));

        // when
        MvcResult result = mockMvc.perform(patch("/users/me/background-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundImageKey\":\"" + key + "\"}"))
                .andReturn();

        // then -> 수정 응답은 보낸 필드만 담는다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsExactlyEntriesOf(Map.of("backgroundImageKey", key));
    }

    @Test
    @DisplayName("배경 반영: 빈 key는 400이고 반영하지 않는다")
    void 배경_반영은_빈_key에_400이다() throws Exception {
        // when -> 여기서 막지 않으면 VO 검증에 걸려 500이 된다
        MvcResult result = mockMvc.perform(patch("/users/me/background-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundImageKey\":\"   \"}"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "배경 이미지 키는 필수입니다.");
        then(changeBackgroundImageUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배경 반영: 255자를 넘는 key는 400이다")
    void 배경_반영은_긴_key에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(patch("/users/me/background-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundImageKey\":\"" + "a".repeat(256) + "\"}"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("message", "배경 이미지 키는 255자 이하여야 합니다.");
        then(changeBackgroundImageUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배경 반영: 검증 실패는 INVALID_BACKGROUND_IMAGE 400으로 그대로 노출한다")
    void 배경_반영_검증_실패는_400으로_노출한다() throws Exception {
        // given
        String key = "profiles/" + VIEWER_ID + "/me.jpg";
        given(changeBackgroundImageUsecase.handle(new ChangeBackgroundImageCommand(VIEWER_ID, key)))
                .willThrow(new InvalidBackgroundImageException());

        // when
        MvcResult result = mockMvc.perform(patch("/users/me/background-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundImageKey\":\"" + key + "\"}"))
                .andReturn();

        // then -> toStatus에서 빠지면 500으로 가려진다
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_BACKGROUND_IMAGE")
                .containsEntry("message", "배경 이미지가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("배경 반영: 올라가지 않은 key는 BACKGROUND_IMAGE_NOT_UPLOADED 400으로 그대로 노출한다")
    void 배경_반영_미업로드는_400으로_노출한다() throws Exception {
        // given
        String key = "backgrounds/" + VIEWER_ID + "/bg.jpg";
        given(changeBackgroundImageUsecase.handle(new ChangeBackgroundImageCommand(VIEWER_ID, key)))
                .willThrow(new BackgroundImageNotUploadedException());

        // when
        MvcResult result = mockMvc.perform(patch("/users/me/background-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backgroundImageKey\":\"" + key + "\"}"))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "BACKGROUND_IMAGE_NOT_UPLOADED")
                .containsEntry("message", "업로드되지 않은 이미지입니다.");
    }

    @Test
    @DisplayName("배경 조회: 경로의 v7 userId를 그대로 유스케이스에 넘긴다")
    void 배경_조회는_경로의_userId를_넘긴다() throws Exception {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(getBackgroundImageUsecase.handle(new GetBackgroundImageUrlQuery(userId)))
                .willReturn(new GetBackgroundImageUrlResult(userId, "https://cdn.example.com/bg.jpg"));

        // when
        MvcResult result = mockMvc.perform(get("/users/" + userId + "/background-image")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsEntry("backgroundImageUrl", "https://cdn.example.com/bg.jpg");
    }

    @Test
    @DisplayName("배경 조회: 배경이 없으면 backgroundImageUrl을 null로 담는다")
    void 배경_조회는_없으면_null을_담는다() throws Exception {
        // given
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        given(getBackgroundImageUsecase.handle(new GetBackgroundImageUrlQuery(userId)))
                .willReturn(new GetBackgroundImageUrlResult(userId, null));

        // when
        MvcResult result = mockMvc.perform(get("/users/" + userId + "/background-image")).andReturn();

        // then -> 키를 빼지 않고 null로 내린다
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).containsKey("backgroundImageUrl");
        assertThat(body(result).get("backgroundImageUrl")).isNull();
    }

    @Test
    @DisplayName("배경 조회: v7이 아닌 userId는 400이고 조회하지 않는다")
    void 배경_조회는_v7이_아닌_userId에_400이다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/users/" + NON_V7_USER_ID + "/background-image")).andReturn();

        // then
        assertInvalidRequest(result);
        then(getBackgroundImageUsecase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배경 삭제: 토큰의 userId로 연결을 끊고 204를 돌려준다")
    void 배경_삭제는_204다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(delete("/users/me/background-image")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        then(deleteBackgroundImageUsecase).should().handle(new DeleteBackgroundImageCommand(VIEWER_ID));
    }

    private static void assertInvalidRequest(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result))
                .containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "입력값이 올바르지 않습니다.");
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }
}
