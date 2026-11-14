package com.runiverse.running_service.unit_test.recordcard.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageTooLargeException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftInProgressException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageNotUploadedException;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.in.CreateRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.infrastructure.config.JacksonConfig;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import com.runiverse.running_service.presentation.recordcard.controller.RecordCardController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 9-1 작성 요청 — 본문 검증(400)은 컨트롤러가, 크기 상한·404·409는 유스케이스가 만든다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 컨트롤러 단위 테스트")
class RecordCardControllerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID DRAFT_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400d1");
    private static final UUID IMAGE_ID_1 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");
    private static final UUID IMAGE_ID_2 = UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e");
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(JacksonConfig.SECONDS)))
            .build();

    @Mock
    private CreateRecordCardDraftUsecase createRecordCardDraftUsecase;

    @Mock
    private CompleteRecordCardDraftUsecase completeRecordCardDraftUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new RecordCardController(createRecordCardDraftUsecase,
                completeRecordCardDraftUsecase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON_MAPPER))
                .build();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("작성 요청을 토큰 주인의 Command로 넘기고, 200으로 초안 ID·만료 시각·업로드 목록을 내려 준다")
    void createDraft() throws Exception {
        // given
        given(createRecordCardDraftUsecase.handle(any())).willReturn(new CreateRecordCardDraftResult(
                DRAFT_ID, LocalDateTime.of(2026, 11, 13, 21, 40), List.of(
                new Upload.Single(IMAGE_ID_1, "record-cards/u/c/i1/IMG_2034.jpg", "https://single"),
                new Upload.Multipart(IMAGE_ID_2, "record-cards/u/c/i2/한강 야경.png", 10_485_760L, List.of(
                        new MultipartUpload.Part(1, "https://p1"),
                        new MultipartUpload.Part(2, "https://p2"))))));

        // when
        MvcResult result = mockMvc.perform(post("/record-cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"runningRecordId":501,"activityType":"DAILY","memo":"바람이 좋았다",
                                 "visibility":"PUBLIC","thumbnailIndex":1,"images":[
                                  {"mimeType":"image/jpeg","fileSizeBytes":204800,"fileName":"IMG_2034.jpg",
                                   "caption":"노을"},
                                  {"mimeType":"image/png","fileSizeBytes":20971520,"fileName":"한강 야경.png"}]}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ArgumentCaptor<CreateRecordCardDraftCommand> command = ArgumentCaptor.forClass(
                CreateRecordCardDraftCommand.class);
        verify(createRecordCardDraftUsecase).handle(command.capture());
        assertThat(command.getValue()).isEqualTo(new CreateRecordCardDraftCommand(USER_ID, 501L, "DAILY",
                "바람이 좋았다", "PUBLIC", List.of(
                new CreateRecordCardDraftCommand.Image("image/jpeg", 204800L, "IMG_2034.jpg", "노을"),
                new CreateRecordCardDraftCommand.Image("image/png", 20971520L, "한강 야경.png", null)), 1));

        Map<String, Object> body = body(result);
        assertThat(body.keySet()).containsExactly("recordCardDraftId", "expiresAt", "uploads");
        assertThat(body).containsEntry("recordCardDraftId", DRAFT_ID.toString())
                .containsEntry("expiresAt", "2026-11-13T21:40:00");
        // 올리는 방식과 상관없이 키는 모두 내려가고, 해당 없는 값은 null이다
        assertThat(body.get("uploads")).isEqualTo(List.of(
                upload(IMAGE_ID_1, "record-cards/u/c/i1/IMG_2034.jpg", "SINGLE", "https://single", null, null),
                upload(IMAGE_ID_2, "record-cards/u/c/i2/한강 야경.png", "MULTIPART", null, 10_485_760, List.of(
                        Map.of("partNumber", 1, "uploadUrl", "https://p1"),
                        Map.of("partNumber", 2, "uploadUrl", "https://p2")))));
    }

    @Test
    @DisplayName("사진을 생략하면 빈 목록으로 넘긴다")
    void omittedImagesBecomeEmpty() throws Exception {
        // given
        given(createRecordCardDraftUsecase.handle(any())).willReturn(
                new CreateRecordCardDraftResult(DRAFT_ID, LocalDateTime.of(2026, 11, 13, 21, 40), List.of()));

        // when
        MvcResult result = mockMvc.perform(post("/record-cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"runningRecordId":501,"activityType":"RACE","memo":"완주","visibility":"PRIVATE"}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ArgumentCaptor<CreateRecordCardDraftCommand> command = ArgumentCaptor.forClass(
                CreateRecordCardDraftCommand.class);
        verify(createRecordCardDraftUsecase).handle(command.capture());
        assertThat(command.getValue().images()).isEmpty();
        assertThat(command.getValue().thumbnailIndex()).isNull();
        assertThat(body(result).get("uploads")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("본문 검증에 실패하면 400으로 명세 문구를 내려 주고 유스케이스를 부르지 않는다")
    void rejectInvalidBody() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/record-cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"runningRecordId":501,"activityType":"DAILY","memo":"글","visibility":"PUBLIC",
                                 "images":[{"mimeType":"image/jpeg","fileSizeBytes":1,"fileName":"../a.jpg"}]}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "파일 이름에 쓸 수 없는 문자가 있습니다.");
        verifyNoInteractions(createRecordCardDraftUsecase);
    }

    @Test
    @DisplayName("사진이 최대 크기를 넘으면 유스케이스가 만든 문구로 400을 내려 준다")
    void tooLargeImageIs400() throws Exception {
        // given
        given(createRecordCardDraftUsecase.handle(any())).willThrow(new RecordCardImageTooLargeException(100));

        // when
        MvcResult result = perform();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "이미지는 100MB 이하만 업로드할 수 있습니다.");
    }

    @Test
    @DisplayName("카드를 붙일 수 없는 기록이면 404를 내려 준다")
    void notFoundRecordIs404() throws Exception {
        // given
        given(createRecordCardDraftUsecase.handle(any())).willThrow(new RunningRecordNotFoundException());

        // when
        MvcResult result = perform();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("이미 카드가 있는 기록이면 409를 가리지 않고 내려 준다")
    void existingCardIs409() throws Exception {
        // given
        given(createRecordCardDraftUsecase.handle(any())).willThrow(new RecordCardAlreadyExistsException());

        // when
        MvcResult result = perform();

        // then -> 노출 목록에서 빠지면 500으로 가려진다
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result)).containsEntry("code", "RECORD_CARD_ALREADY_EXISTS")
                .containsEntry("message", "이미 기록 카드가 있는 러닝입니다.");
    }

    @Test
    @DisplayName("확정은 토큰 주인과 경로의 초안 ID로 넘기고, 201로 카드 ID만 내려 준다")
    void completeDraft() throws Exception {
        // given
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
        given(completeRecordCardDraftUsecase.handle(any())).willReturn(new CompleteRecordCardDraftResult(cardId));

        // when
        MvcResult result = mockMvc.perform(post("/record-cards/drafts/{id}/complete", DRAFT_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        verify(completeRecordCardDraftUsecase).handle(new CompleteRecordCardDraftCommand(USER_ID, DRAFT_ID));
        assertThat(body(result)).isEqualTo(Map.of("recordCardId", cardId.toString()));
    }

    @Test
    @DisplayName("초안 ID가 UUIDv7 형식이 아니면 400이고 유스케이스를 부르지 않는다")
    void rejectMalformedDraftId() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/record-cards/drafts/{id}/complete", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        verifyNoInteractions(completeRecordCardDraftUsecase);
    }

    @Test
    @DisplayName("확정의 실패는 명세대로 404·409·400을 그대로 내려 준다")
    void completeDraftErrors() throws Exception {
        // given
        given(completeRecordCardDraftUsecase.handle(any()))
                .willThrow(new RecordCardDraftNotFoundException())
                .willThrow(new RecordCardDraftInProgressException())
                .willThrow(new RecordCardImageNotUploadedException());

        // when & then
        MvcResult notFound = mockMvc.perform(post("/record-cards/drafts/{id}/complete", DRAFT_ID)).andReturn();
        assertThat(notFound.getResponse().getStatus()).isEqualTo(404);

        MvcResult inProgress = mockMvc.perform(post("/record-cards/drafts/{id}/complete", DRAFT_ID)).andReturn();
        assertThat(inProgress.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(inProgress)).containsEntry("code", "RECORD_CARD_DRAFT_IN_PROGRESS")
                .containsEntry("message", "기록 카드를 저장하고 있습니다. 잠시 후 다시 시도해 주세요.");

        MvcResult notUploaded = mockMvc.perform(post("/record-cards/drafts/{id}/complete", DRAFT_ID)).andReturn();
        assertThat(notUploaded.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(notUploaded)).containsEntry("code", "RECORD_CARD_IMAGE_NOT_UPLOADED")
                .containsEntry("message", "업로드되지 않은 이미지입니다.");
    }

    private MvcResult perform() throws Exception {
        return mockMvc.perform(post("/record-cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"runningRecordId":501,"activityType":"DAILY","memo":"글","visibility":"PUBLIC"}
                                """))
                .andReturn();
    }

    private static Map<String, Object> body(MvcResult result) throws Exception {
        return JSON_MAPPER.readValue(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }

    // null 값을 담아야 해서 Map.of 대신 HashMap을 쓴다
    private static Map<String, Object> upload(UUID id, String key, String type, String url, Integer partSize,
                                              List<?> parts) {
        Map<String, Object> upload = new HashMap<>();
        upload.put("recordCardImageId", id.toString());
        upload.put("imageKey", key);
        upload.put("uploadType", type);
        upload.put("uploadUrl", url);
        upload.put("partSizeBytes", partSize);
        upload.put("parts", parts);
        return upload;
    }
}
