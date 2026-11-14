package com.runiverse.e2e.recordcard;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.RunningWebSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 기록 카드 작성(9-1 작성 요청 → S3 업로드 → 9-2 확정) E2E 테스트")
class RecordCardE2eTest extends E2eTestSupport {

    // 기록으로 남으려면 최소 100m·60초를 넘겨야 한다 — SoloRunningE2eTest와 같은 트랙이다
    private static final int POINT_COUNT = 61;
    private static final int SECONDS_PER_STEP = 2;
    private static final double METERS_PER_STEP = 5.0;
    private static final int BATCH_SIZE = 10;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;
    private static final double START_LATITUDE = 37.5665;
    private static final double START_LONGITUDE = 126.9780;
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration FINISH_TIMEOUT = Duration.ofSeconds(30);

    // application.properties의 record-card.draft.* — 운영값이 바뀌면 같이 고친다
    private static final int PART_SIZE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_IMAGE_SIZE_BYTES = 100L * 1024 * 1024;
    private static final int SMALL_IMAGE_BYTES = 2048;
    private static final Pattern UPLOAD_ID = Pattern.compile("(?:^|&)uploadId=([^&]+)");

    private final LocalDateTime trackStartAt = LocalDateTime.now(APP_ZONE)
            .minusSeconds((long) POINT_COUNT * SECONDS_PER_STEP);

    @Test
    @DisplayName("작성 요청 → 사진 업로드 → 확정으로 카드와 사진 행이 생기고, 같은 초안으로 다시 확정해도 같은 카드다")
    void singleUploadFlow() {
        // given - 기록이 있는 사용자가 사진 두 장으로 작성 요청을 보낸다
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "first.jpg", "출발선"),
                image("image/png", SMALL_IMAGE_BYTES, "second.png", null)), 1), user.accessToken());
        assertThat(draft.status()).isEqualTo(200);
        assertThat(draft.text("recordCardDraftId")).isNotBlank();
        assertThat(draft.text("expiresAt")).isNotBlank();
        List<Map<String, Object>> uploads = draft.objects("uploads");
        assertThat(uploads).hasSize(2);
        assertThat(uploads).allSatisfy(upload -> {
            assertThat(upload.get("uploadType")).isEqualTo("SINGLE");
            assertThat(upload.get("uploadUrl")).isNotNull();
            assertThat(upload.get("parts")).isNull();
        });
        // key는 record-cards/{userId}/{recordCardId}/{recordCardImageId}/{원래 파일 이름}이다
        String firstKey = (String) uploads.get(0).get("imageKey");
        String secondKey = (String) uploads.get(1).get("imageKey");
        assertThat(firstKey).startsWith("record-cards/" + user.userId() + "/")
                .endsWith("/" + uploads.get(0).get("recordCardImageId") + "/first.jpg");
        assertThat(secondKey).endsWith("/" + uploads.get(1).get("recordCardImageId") + "/second.png");

        uploadRecordCardImage(firstKey, "image/jpeg", SMALL_IMAGE_BYTES);
        uploadRecordCardImage(secondKey, "image/png", SMALL_IMAGE_BYTES);

        // when
        String completePath = completePath(draft.text("recordCardDraftId"));
        Response completed = post(completePath, Map.of(), user.accessToken());

        // then - 카드가 생긴다
        assertThat(completed.status()).isEqualTo(201);
        String recordCardId = completed.text("recordCardId");
        assertThat(firstKey).contains("/" + recordCardId + "/");

        // 확정이 손으로 쓴 스키마가 아니라 앱이 만든 스키마 위에서도 제약을 지키며 들어갔는지 행으로 본다
        assertThat(querySql("""
                SELECT running_record_id, user_id, visibility, activity_type, memo, display_order,
                       base_music_status, coalesce(base_music_key, '-'), thumbnail_image_key
                FROM record_cards WHERE record_card_id = '%s'
                """.formatted(recordCardId)))
                .containsExactly(String.join("|", String.valueOf(runningRecordId), user.userId(), "PUBLIC",
                        "DAILY", "한강 한 바퀴", "1", "GENERATING", "-", secondKey));
        assertThat(querySql("""
                SELECT record_card_image_id, sort_order, image_key, coalesce(caption, '-'),
                       music_status, coalesce(music_key, '-')
                FROM record_card_images WHERE record_card_id = '%s' ORDER BY sort_order
                """.formatted(recordCardId)))
                .containsExactly(
                        String.join("|", (String) uploads.get(0).get("recordCardImageId"), "0", firstKey, "출발선",
                                "GENERATING", "-"),
                        String.join("|", (String) uploads.get(1).get("recordCardImageId"), "1", secondKey, "-",
                                "GENERATING", "-"));

        // 응답을 놓친 앱이 같은 초안으로 다시 확정해도 새 카드가 생기지 않는다
        Response retried = post(completePath, Map.of(), user.accessToken());
        assertThat(retried.status()).isEqualTo(201);
        assertThat(retried.text("recordCardId")).isEqualTo(recordCardId);
        assertThat(querySql("SELECT count(*) FROM record_cards WHERE running_record_id = " + runningRecordId))
                .containsExactly("1");
    }

    @Test
    @DisplayName("파트 크기를 넘는 사진은 나눠 올리고, 확정이 조각을 합친 뒤 카드를 만든다")
    void multipartUploadFlow() {
        // given - 파트 크기보다 1MB 큰 사진은 두 조각이다(10MB + 1MB)
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        int imageBytes = PART_SIZE_BYTES + 1024 * 1024;
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", imageBytes, "big.jpg", null)), null), user.accessToken());
        assertThat(draft.status()).isEqualTo(200);
        Map<String, Object> upload = draft.objects("uploads").get(0);
        assertThat(upload.get("uploadType")).isEqualTo("MULTIPART");
        assertThat(upload.get("uploadUrl")).isNull();
        assertThat(((Number) upload.get("partSizeBytes")).intValue()).isEqualTo(PART_SIZE_BYTES);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) upload.get("parts");
        assertThat(parts).extracting(part -> part.get("partNumber")).containsExactly(1, 2);

        String key = (String) upload.get("imageKey");
        String uploadId = uploadId((String) parts.get(0).get("uploadUrl"));
        uploadRecordCardPart(key, uploadId, 1, PART_SIZE_BYTES);
        uploadRecordCardPart(key, uploadId, 2, imageBytes - PART_SIZE_BYTES);

        // when
        Response completed = post(completePath(draft.text("recordCardDraftId")), Map.of(), user.accessToken());

        // then - 합친 객체의 크기가 작성 요청과 같아 확정된다
        assertThat(completed.status()).isEqualTo(201);
        assertThat(querySql("SELECT image_key FROM record_card_images WHERE record_card_id = '"
                + completed.text("recordCardId") + "'"))
                .containsExactly(key);
    }

    @Test
    @DisplayName("사진 없이도 카드를 만들 수 있고, 같은 사람의 두 번째 카드는 순위 2다")
    void cardWithoutImagesAndDisplayOrder() {
        // given
        TestUser user = signUpAndOnboard();
        long firstRecordId = finishSoloRun(user);
        long secondRecordId = finishSoloRun(user);

        // when
        String firstCardId = createCardWithoutImages(user, firstRecordId);
        String secondCardId = createCardWithoutImages(user, secondRecordId);

        // then - 썸네일은 사진이 없어 null이다
        assertThat(querySql("""
                SELECT record_card_id, display_order, coalesce(thumbnail_image_key, '-')
                FROM record_cards WHERE user_id = '%s' ORDER BY display_order
                """.formatted(user.userId())))
                .containsExactly(firstCardId + "|1|-", secondCardId + "|2|-");
    }

    @Test
    @DisplayName("같은 초안을 동시에 확정해도 카드는 하나다 — 늦은 쪽은 409이거나 같은 카드 ID를 받는다")
    void concurrentCompleteCreatesOneCard() throws Exception {
        // given
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "a.jpg", null)), null), user.accessToken());
        String key = (String) draft.objects("uploads").get(0).get("imageKey");
        uploadRecordCardImage(key, "image/jpeg", SMALL_IMAGE_BYTES);
        String completePath = completePath(draft.text("recordCardDraftId"));

        // when - 두 요청을 같은 순간에 출발시킨다
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Response>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return post(completePath, Map.of(), user.accessToken());
            }));
        }
        start.countDown();
        List<Response> responses = new ArrayList<>();
        for (CompletableFuture<Response> call : calls) {
            responses.add(call.get());
        }

        // then - 잠금을 놓친 쪽은 기다리지 않고 409, 잠금이 풀린 뒤 들어온 쪽은 확정된 초안을 보고 같은 ID다
        List<Response> created = responses.stream().filter(response -> response.status() == 201).toList();
        assertThat(created).isNotEmpty();
        assertThat(created).extracting(response -> response.text("recordCardId")).containsOnly(
                created.get(0).text("recordCardId"));
        assertThat(responses).allSatisfy(response -> {
            if (response.status() != 201) {
                assertThat(response.status()).isEqualTo(409);
                assertThat(response.text("code")).isEqualTo("RECORD_CARD_DRAFT_IN_PROGRESS");
            }
        });
        assertThat(querySql("SELECT count(*) FROM record_cards WHERE running_record_id = " + runningRecordId))
                .containsExactly("1");
    }

    @Test
    @DisplayName("사진을 다 올리지 않고 확정하면 400이고, 빠진 사진을 올린 뒤 같은 초안으로 확정할 수 있다")
    void missingImageThenRetry() {
        // given - 두 장 중 첫 장만 올린다
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "a.jpg", null),
                image("image/jpeg", SMALL_IMAGE_BYTES, "b.jpg", null)), null), user.accessToken());
        List<Map<String, Object>> uploads = draft.objects("uploads");
        uploadRecordCardImage((String) uploads.get(0).get("imageKey"), "image/jpeg", SMALL_IMAGE_BYTES);
        String completePath = completePath(draft.text("recordCardDraftId"));

        // when
        Response missing = post(completePath, Map.of(), user.accessToken());

        // then - 카드는 생기지 않는다
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.text("code")).isEqualTo("RECORD_CARD_IMAGE_NOT_UPLOADED");
        assertThat(querySql("SELECT count(*) FROM record_cards WHERE running_record_id = " + runningRecordId))
                .containsExactly("0");

        // 빠진 것만 올리고 다시 확정한다 — 실패한 확정이 잠금을 남기지 않았다
        uploadRecordCardImage((String) uploads.get(1).get("imageKey"), "image/jpeg", SMALL_IMAGE_BYTES);
        Response completed = post(completePath, Map.of(), user.accessToken());
        assertThat(completed.status()).isEqualTo(201);
    }

    @Test
    @DisplayName("올라간 사진의 크기·형식이 작성 요청과 다르면 400 INVALID_RECORD_CARD_IMAGE다")
    void uploadedImageMismatch() {
        // given - JPEG로 요청하고 PNG로 올린다
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "a.jpg", null)), null), user.accessToken());
        uploadRecordCardImage((String) draft.objects("uploads").get(0).get("imageKey"), "image/png",
                SMALL_IMAGE_BYTES);

        // when
        Response response = post(completePath(draft.text("recordCardDraftId")), Map.of(), user.accessToken());

        // then
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_RECORD_CARD_IMAGE");
    }

    @Test
    @DisplayName("카드가 이미 있는 기록으로 다시 작성 요청하면 409다")
    void recordAlreadyHasCard() {
        // given
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        createCardWithoutImages(user, runningRecordId);

        // when
        Response response = post("/record-cards", draftRequest(runningRecordId, List.of(), null),
                user.accessToken());

        // then
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("code")).isEqualTo("RECORD_CARD_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("남의 기록으로 작성 요청하거나 남의 초안을 확정하면 404이고, 없는 기록·초안과 구분되지 않는다")
    void othersRecordAndDraftAreHidden() {
        // given
        TestUser owner = signUpAndOnboard();
        TestUser other = signUpAndOnboard();
        long runningRecordId = finishSoloRun(owner);
        Response ownerDraft = post("/record-cards", draftRequest(runningRecordId, List.of(), null),
                owner.accessToken());
        assertThat(ownerDraft.status()).isEqualTo(200);

        // when
        Response othersRecord = post("/record-cards", draftRequest(runningRecordId, List.of(), null),
                other.accessToken());
        Response missingRecord = post("/record-cards", draftRequest(Long.MAX_VALUE, List.of(), null),
                other.accessToken());
        Response othersDraft = post(completePath(ownerDraft.text("recordCardDraftId")), Map.of(),
                other.accessToken());

        // then
        assertThat(othersRecord.status()).isEqualTo(404);
        assertThat(othersRecord.text("code")).isEqualTo("NOT_FOUND");
        assertThat(missingRecord.status()).isEqualTo(404);
        assertThat(missingRecord.text("code")).isEqualTo("NOT_FOUND");
        assertThat(othersDraft.status()).isEqualTo(404);
        assertThat(othersDraft.text("code")).isEqualTo("NOT_FOUND");
        // 남이 확정을 시도해도 주인의 초안은 그대로 확정된다
        assertThat(post(completePath(ownerDraft.text("recordCardDraftId")), Map.of(), owner.accessToken())
                .status()).isEqualTo(201);
    }

    @Test
    @DisplayName("최대 크기를 넘는 사진, UUIDv7이 아닌 초안 ID, 토큰 없는 요청은 막힌다")
    void requestsAreGuarded() {
        // given
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);

        // when
        Response tooLarge = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", MAX_IMAGE_SIZE_BYTES + 1, "huge.jpg", null)), null), user.accessToken());
        Response notUuidV7 = post(completePath("00000000-0000-4000-8000-000000000000"), Map.of(),
                user.accessToken());
        Response anonymous = post("/record-cards", draftRequest(runningRecordId, List.of(), null));

        // then - 크기 초과는 운영값이라 유스케이스가 보지만 클라에는 요청 검증 실패와 같은 코드다
        assertThat(tooLarge.status()).isEqualTo(400);
        assertThat(tooLarge.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(notUuidV7.status()).isEqualTo(400);
        assertThat(notUuidV7.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(anonymous.status()).isEqualTo(401);
    }

    private String createCardWithoutImages(TestUser user, long runningRecordId) {
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(), null), user.accessToken());
        assertThat(draft.status()).isEqualTo(200);
        assertThat(draft.objects("uploads")).isEmpty();
        Response completed = post(completePath(draft.text("recordCardDraftId")), Map.of(), user.accessToken());
        assertThat(completed.status()).isEqualTo(201);
        return completed.text("recordCardId");
    }

    private static Map<String, Object> draftRequest(long runningRecordId, List<Map<String, Object>> images,
                                                    Integer thumbnailIndex) {
        Map<String, Object> request = new HashMap<>();
        request.put("runningRecordId", runningRecordId);
        request.put("activityType", "DAILY");
        request.put("memo", "한강 한 바퀴");
        request.put("visibility", "PUBLIC");
        request.put("images", images);
        if (thumbnailIndex != null) {
            request.put("thumbnailIndex", thumbnailIndex);
        }
        return request;
    }

    private static Map<String, Object> image(String mimeType, long fileSizeBytes, String fileName, String caption) {
        Map<String, Object> image = new HashMap<>();
        image.put("mimeType", mimeType);
        image.put("fileSizeBytes", fileSizeBytes);
        image.put("fileName", fileName);
        if (caption != null) {
            image.put("caption", caption);
        }
        return image;
    }

    private static String completePath(String recordCardDraftId) {
        return "/record-cards/drafts/" + recordCardDraftId + "/complete";
    }

    // 조각 URL은 S3 presigned UploadPart라 쿼리에 uploadId가 실려 있다
    private static String uploadId(String partUploadUrl) {
        Matcher matcher = UPLOAD_ID.matcher(URI.create(partUploadUrl).getRawQuery());
        assertThat(matcher.find()).as("조각 URL에 uploadId가 있어야 한다: %s", partUploadUrl).isTrue();
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    /**
     * 솔로 러닝을 한 번 끝내 기록을 만들고 그 ID를 돌려준다. 카드는 기록에만 붙고 기록은 API로만 생긴다.
     * 같은 사용자가 여러 번 불러도 된다 — 끝난 뒤에는 솔로 방을 다시 열 수 있다.
     */
    private long finishSoloRun(TestUser user) {
        List<Long> before = runningRecordIds(user);
        long runningRoomId = post("/running-rooms/solo", Map.of(), user.accessToken()).number("runningRoomId");
        try (RunningWebSocket socket = connectRunningWebSocket(user.accessToken())) {
            socket.send("RUNNING_START", Map.of("runningRoomId", runningRoomId));
            socket.await("RUNNING_STARTED");
            for (int from = 0; from < POINT_COUNT; from += BATCH_SIZE) {
                List<Map<String, Object>> batch = new ArrayList<>();
                for (int sequence = from; sequence < Math.min(from + BATCH_SIZE, POINT_COUNT); sequence++) {
                    batch.add(point(sequence));
                }
                socket.send("RUNNING_LOCATION_UPDATE", Map.of("locations", batch));
            }
            socket.send("RUNNING_FINISH", Map.of("forced", false));
            socket.await("RUNNING_FINISHED", FINISH_TIMEOUT);
        }
        List<Long> after = new ArrayList<>(runningRecordIds(user));
        after.removeAll(before);
        assertThat(after).as("러닝을 끝내면 기록이 하나 늘어야 한다").hasSize(1);
        return after.get(0);
    }

    private List<Long> runningRecordIds(TestUser user) {
        String day = trackStartAt.toLocalDate().toString();
        Response records = get("/users/me/running-records?from=" + day + "&to=" + day, user.accessToken());
        assertThat(records.status()).isEqualTo(200);
        return records.objects("runningRecords").stream()
                .map(record -> ((Number) record.get("runningRecordId")).longValue())
                .toList();
    }

    private Map<String, Object> point(int sequence) {
        Map<String, Object> point = new HashMap<>();
        point.put("sequence", sequence);
        point.put("latitude", START_LATITUDE + sequence * METERS_PER_STEP / METERS_PER_LATITUDE_DEGREE);
        point.put("longitude", START_LONGITUDE);
        point.put("altitudeMeters", 30.0);
        point.put("accuracyMeters", 5.0);
        point.put("speedMetersPerSecond", METERS_PER_STEP / SECONDS_PER_STEP);
        point.put("headingDegrees", 0.0);
        point.put("cadenceSpm", 170);
        point.put("currentPaceSecondsPerKm", 400);
        point.put("pauseSegment", 0);
        point.put("recordedAt", trackStartAt.plusSeconds((long) sequence * SECONDS_PER_STEP)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return point;
    }
}
