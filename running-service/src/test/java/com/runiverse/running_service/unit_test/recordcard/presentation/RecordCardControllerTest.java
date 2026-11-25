package com.runiverse.running_service.unit_test.recordcard.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.completedraft.CompleteRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftCommand;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult;
import com.runiverse.running_service.application.recordcard.command.createdraft.CreateRecordCardDraftResult.Upload;
import com.runiverse.running_service.application.recordcard.command.delete.DeleteRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.retrymusic.RetryRecordCardMusicCommand;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.update.UpdateRecordCardResult;
import com.runiverse.running_service.application.recordcard.exception.RecordCardAlreadyExistsException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageTooLargeException;
import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftInProgressException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardDraftNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardImageNotUploadedException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardThumbnailOutOfRangeException;
import com.runiverse.running_service.application.recordcard.port.in.CompleteRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.in.CreateRecordCardDraftUsecase;
import com.runiverse.running_service.application.recordcard.port.in.DeleteRecordCardUsecase;
import com.runiverse.running_service.application.recordcard.port.in.RetryRecordCardMusicUsecase;
import com.runiverse.running_service.application.recordcard.port.in.UpdateRecordCardUsecase;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardDetailUsecase;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.application.recordcard.common.RouteArtConverter;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailQuery;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @Mock
    private GetRecordCardDetailUsecase getRecordCardDetailUsecase;
    @Mock
    private DeleteRecordCardUsecase deleteRecordCardUsecase;
    @Mock
    private UpdateRecordCardUsecase updateRecordCardUsecase;
    @Mock
    private RetryRecordCardMusicUsecase retryRecordCardMusicUsecase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new RecordCardController(createRecordCardDraftUsecase,
                completeRecordCardDraftUsecase, getRecordCardDetailUsecase,
                deleteRecordCardUsecase, updateRecordCardUsecase, retryRecordCardMusicUsecase))
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
                new Upload.Single(IMAGE_ID_1, "record-cards/u/c/i1/IMG_2034.jpg", "https://single",
                        new Upload.Thumbnail("record-cards/u/c/i1/thumbnail/IMG_2034.jpg", "https://thumb1")),
                new Upload.Multipart(IMAGE_ID_2, "record-cards/u/c/i2/한강 야경.png", 10_485_760L, List.of(
                        new MultipartUpload.Part(1, "https://p1"),
                        new MultipartUpload.Part(2, "https://p2")),
                        new Upload.Thumbnail("record-cards/u/c/i2/thumbnail/한강 야경.png", "https://thumb2")))));

        // when
        MvcResult result = mockMvc.perform(post("/record-cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"runningRecordId":501,"activityType":"DAILY","memo":"바람이 좋았다",
                                 "visibility":"PUBLIC","thumbnailIndex":1,"images":[
                                  {"mimeType":"image/jpeg","fileSizeBytes":204800,"thumbnailFileSizeBytes":100,
                                   "fileName":"IMG_2034.jpg","caption":"노을"},
                                  {"mimeType":"image/png","fileSizeBytes":20971520,"thumbnailFileSizeBytes":100,
                                   "fileName":"한강 야경.png"}]}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ArgumentCaptor<CreateRecordCardDraftCommand> command = ArgumentCaptor.forClass(
                CreateRecordCardDraftCommand.class);
        verify(createRecordCardDraftUsecase).handle(command.capture());
        assertThat(command.getValue()).isEqualTo(new CreateRecordCardDraftCommand(USER_ID, 501L, "DAILY",
                "바람이 좋았다", "PUBLIC", List.of(
                new CreateRecordCardDraftCommand.Image("image/jpeg", 204800L, 100L, "IMG_2034.jpg", "노을"),
                new CreateRecordCardDraftCommand.Image("image/png", 20971520L, 100L, "한강 야경.png", null)), 1));

        Map<String, Object> body = body(result);
        assertThat(body.keySet()).containsExactly("recordCardDraftId", "expiresAt", "uploads");
        assertThat(body).containsEntry("recordCardDraftId", DRAFT_ID.toString())
                .containsEntry("expiresAt", "2026-11-13T21:40:00");
        // 올리는 방식과 상관없이 키는 모두 내려가고, 해당 없는 값은 null이다
        assertThat(body.get("uploads")).isEqualTo(List.of(
                upload(IMAGE_ID_1, "record-cards/u/c/i1/IMG_2034.jpg", "SINGLE", "https://single", null, null,
                        "record-cards/u/c/i1/thumbnail/IMG_2034.jpg", "https://thumb1"),
                upload(IMAGE_ID_2, "record-cards/u/c/i2/한강 야경.png", "MULTIPART", null, 10_485_760, List.of(
                        Map.of("partNumber", 1, "uploadUrl", "https://p1"),
                        Map.of("partNumber", 2, "uploadUrl", "https://p2")),
                        "record-cards/u/c/i2/thumbnail/한강 야경.png", "https://thumb2")));
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
                                 "images":[{"mimeType":"image/jpeg","fileSizeBytes":1,"thumbnailFileSizeBytes":1,
                                            "fileName":"../a.jpg"}]}
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

    @Test
    @DisplayName("상세는 토큰 주인과 경로의 카드 ID로 넘기고, 200으로 명세의 공통 객체를 내려 준다 — 경로는 [x, y] 선 아트다")
    void getDetail() throws Exception {
        // given
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
        UUID authorId = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
        LocalDateTime at = LocalDateTime.of(2026, 7, 25, 21, 10);
        given(getRecordCardDetailUsecase.handle(any())).willReturn(new GetRecordCardDetailResult(
                cardId,
                new GetRecordCardDetailResult.Author(authorId, "동완러너", null),
                true, RecordCardVisibility.PUBLIC, RecordCardActivityType.DAILY, "한강 야간 러닝",
                new GetRecordCardDetailResult.Record(501L, LocalDateTime.of(2026, 7, 25, 19, 0, 30), 5020, 1800, 359,
                        List.of(List.of(new RouteArtConverter.Point(0.1234, 0.8801),
                                new RouteArtConverter.Point(0.5, 0.0)))),
                new GetRecordCardDetailResult.Music(MusicStatus.COMPLETED, "https://base"),
                List.of(new GetRecordCardDetailResult.Image(IMAGE_ID_1, "https://a", null, true,
                        new GetRecordCardDetailResult.Music(MusicStatus.GENERATING, null))), List.of(),
                at, at));

        // when
        MvcResult result = mockMvc.perform(get("/record-cards/{id}", cardId)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        verify(getRecordCardDetailUsecase).handle(new GetRecordCardDetailQuery(USER_ID, cardId));
        Map<String, Object> author = new HashMap<>();
        author.put("userId", authorId.toString());
        author.put("nickname", "동완러너");
        author.put("profileImageUrl", null);
        Map<String, Object> imageMusic = new HashMap<>();
        imageMusic.put("status", "GENERATING");
        imageMusic.put("musicUrl", null);
        Map<String, Object> image = new HashMap<>();
        image.put("recordCardImageId", IMAGE_ID_1.toString());
        image.put("imageUrl", "https://a");
        image.put("caption", null);
        image.put("isThumbnail", true);
        image.put("music", imageMusic);
        assertThat(body(result)).isEqualTo(Map.ofEntries(
                Map.entry("recordCardId", cardId.toString()),
                Map.entry("author", author),
                Map.entry("isMine", true),
                Map.entry("visibility", "PUBLIC"),
                Map.entry("activityType", "DAILY"),
                Map.entry("memo", "한강 야간 러닝"),
                Map.entry("record", Map.of(
                        "runningRecordId", 501,
                        "startedAt", "2026-07-25T19:00:30",
                        "totalDistanceMeters", 5020,
                        "totalDurationSeconds", 1800,
                        "averagePaceSecondsPerKm", 359,
                        "routeArt", List.of(List.of(List.of(0.1234, 0.8801), List.of(0.5, 0.0))))),
                Map.entry("baseMusic", Map.of("status", "COMPLETED", "musicUrl", "https://base")),
                Map.entry("images", List.of(image)),
                Map.entry("companions", List.of()),
                Map.entry("createdAt", "2026-07-25T21:10:00"),
                Map.entry("updatedAt", "2026-07-25T21:10:00")));
    }

    @Test
    @DisplayName("카드 ID가 UUIDv7 형식이 아니면 400이고 유스케이스를 부르지 않는다")
    void rejectMalformedCardId() throws Exception {
        // when
        MvcResult result = mockMvc.perform(get("/record-cards/{id}", "1-1-1-1-1")).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST");
        verifyNoInteractions(getRecordCardDetailUsecase);
    }

    @Test
    @DisplayName("볼 수 없는 카드는 404로 내려 준다")
    void detailNotFound() throws Exception {
        // given
        given(getRecordCardDetailUsecase.handle(any())).willThrow(new RecordCardNotFoundException());

        // when
        MvcResult result = mockMvc.perform(get("/record-cards/{id}", DRAFT_ID)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).containsEntry("code", "NOT_FOUND")
                .containsEntry("message", "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("삭제는 토큰 주인과 경로의 카드 ID로 넘기고 204로 본문 없이 답한다")
    void deleteCard() throws Exception {
        // given
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");

        // when
        MvcResult result = mockMvc.perform(delete("/record-cards/{id}", cardId)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verify(deleteRecordCardUsecase).handle(new DeleteRecordCardCommand(USER_ID, cardId));
    }

    @Test
    @DisplayName("삭제할 카드 ID가 UUIDv7 형식이 아니면 400, 없거나 남의 카드면 404다")
    void deleteErrors() throws Exception {
        // when & then
        MvcResult malformed = mockMvc.perform(delete("/record-cards/{id}", "1-1-1-1-1")).andReturn();
        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(deleteRecordCardUsecase);

        org.mockito.BDDMockito.willThrow(new RecordCardNotFoundException()).given(deleteRecordCardUsecase).handle(any());
        MvcResult notFound = mockMvc.perform(delete("/record-cards/{id}", DRAFT_ID)).andReturn();
        assertThat(notFound.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(notFound)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @DisplayName("다시 만들기는 토큰 주인과 경로의 카드 ID로 넘기고, 204로 본문 없이 답한다")
    void retryMusic() throws Exception {
        // given
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");

        // when
        MvcResult result = mockMvc.perform(post("/record-cards/{id}/retry", cardId)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        verify(retryRecordCardMusicUsecase).handle(new RetryRecordCardMusicCommand(USER_ID, cardId));
    }

    @Test
    @DisplayName("다시 만들 카드 ID가 UUIDv7 형식이 아니면 400, 없거나 남의 카드면 404다")
    void retryMusicErrors() throws Exception {
        // when & then
        MvcResult malformed = mockMvc.perform(post("/record-cards/{id}/retry", UUID.randomUUID())).andReturn();
        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(retryRecordCardMusicUsecase);

        org.mockito.BDDMockito.willThrow(new RecordCardNotFoundException()).given(retryRecordCardMusicUsecase)
                .handle(any());
        MvcResult notFound = mockMvc.perform(post("/record-cards/{id}/retry", DRAFT_ID)).andReturn();
        assertThat(notFound.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(notFound)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("수정은 늘 200이다 — 새 사진이 없으면 draft가 null이고 카드는 수정 뒤다")
    void updateApplied() throws Exception {
        // given
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
        given(updateRecordCardUsecase.handle(any())).willReturn(new UpdateRecordCardResult(detailResult(cardId), null));

        // when
        MvcResult result = mockMvc.perform(patch("/record-cards/{id}", cardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memo":"새 회고","visibility":"PRIVATE",
                                 "images":[{"recordCardImageId":"%s","caption":"노을"}],"thumbnailIndex":0}
                                """.formatted(IMAGE_ID_1)))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        verify(updateRecordCardUsecase).handle(new UpdateRecordCardCommand(USER_ID, cardId, "새 회고", null, "PRIVATE",
                List.of(new UpdateRecordCardCommand.Image(IMAGE_ID_1, null, null, null, null, "노을")), 0));
        Map<String, Object> body = body(result);
        assertThat(body).containsKey("draft").containsEntry("draft", null);
        assertThat((Map<String, Object>) body.get("recordCard")).containsEntry("recordCardId", cardId.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("새 사진이 있으면 200에 draft(9-1과 같은 모양)와 지금 카드를 함께 준다")
    void updateNeedsUpload() throws Exception {
        // given
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
        given(updateRecordCardUsecase.handle(any())).willReturn(new UpdateRecordCardResult(detailResult(cardId),
                new CreateRecordCardDraftResult(DRAFT_ID, LocalDateTime.of(2026, 11, 23, 21, 40), List.of(
                        new Upload.Single(IMAGE_ID_2, "record-cards/u/c/i2/IMG_3001.jpg", "https://single",
                                new Upload.Thumbnail("record-cards/u/c/i2/thumbnail/IMG_3001.jpg",
                                        "https://thumb"))))));

        // when
        MvcResult result = mockMvc.perform(patch("/record-cards/{id}", cardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"mimeType":"image/jpeg","fileSizeBytes":1024,"thumbnailFileSizeBytes":100,
                                            "fileName":"IMG_3001.jpg"}]}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> draft = (Map<String, Object>) body(result).get("draft");
        assertThat(draft).containsEntry("recordCardDraftId", DRAFT_ID.toString())
                .containsEntry("expiresAt", "2026-11-23T21:40:00");
        assertThat((List<?>) draft.get("uploads")).hasSize(1);
        assertThat((Map<String, Object>) body(result).get("recordCard")).containsEntry("recordCardId", cardId.toString());
    }

    @Test
    @DisplayName("수정 요청 검증에 실패하면 400으로 명세 문구를 내려 주고 유스케이스를 부르지 않는다")
    void updateValidation() throws Exception {
        // when
        MvcResult result = mockMvc.perform(patch("/record-cards/{id}", DRAFT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[{"recordCardImageId":"%1$s"},{"recordCardImageId":"%1$s"}]}
                                """.formatted(IMAGE_ID_1)))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("message", "같은 사진을 두 번 넣을 수 없습니다.");
        verifyNoInteractions(updateRecordCardUsecase);
    }

    @Test
    @DisplayName("사진 목록 없이 보낸 썸네일 위치가 범위 밖이라 유스케이스가 거절하면 400으로 내려 준다")
    void updateThumbnailOutOfRangeIs400() throws Exception {
        // given -> 지금 사진 수는 유스케이스만 알아 DTO가 아니라 유스케이스가 거절한다
        given(updateRecordCardUsecase.handle(any())).willThrow(new RecordCardThumbnailOutOfRangeException());

        // when
        MvcResult result = mockMvc.perform(patch("/record-cards/{id}", DRAFT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"thumbnailIndex":2}
                                """))
                .andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", "INVALID_REQUEST")
                .containsEntry("message", "썸네일 위치가 사진 범위를 벗어났습니다.");
    }

    private static GetRecordCardDetailResult detailResult(UUID cardId) {
        LocalDateTime at = LocalDateTime.of(2026, 7, 25, 21, 10);
        return new GetRecordCardDetailResult(cardId,
                new GetRecordCardDetailResult.Author(USER_ID, "동완러너", null), true,
                RecordCardVisibility.PUBLIC, RecordCardActivityType.DAILY, "한강",
                new GetRecordCardDetailResult.Record(501L, at, 5020, 1800, 359, List.of()),
                new GetRecordCardDetailResult.Music(MusicStatus.GENERATING, null), List.of(), List.of(), at, at);
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
                                              List<?> parts, String thumbnailKey, String thumbnailUrl) {
        Map<String, Object> upload = new HashMap<>();
        upload.put("recordCardImageId", id.toString());
        upload.put("imageKey", key);
        upload.put("uploadType", type);
        upload.put("uploadUrl", url);
        upload.put("partSizeBytes", partSize);
        upload.put("parts", parts);
        upload.put("thumbnailImageKey", thumbnailKey);
        upload.put("thumbnailUploadUrl", thumbnailUrl);
        return upload;
    }
}
