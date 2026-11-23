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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("배포 이미지 대상 기록 카드 작성(9-1 작성 요청 → S3 업로드 → 9-2 확정)·상세(8-1) E2E 테스트")
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
    // 사진마다 함께 올리는 작은 버전(썸네일) 크기 — 운영 상한(record-card.draft.max-thumbnail-size, 1MB) 아래다
    private static final int THUMBNAIL_BYTES = 512;
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

        uploadPhoto(firstKey, "image/jpeg", SMALL_IMAGE_BYTES);
        uploadPhoto(secondKey, "image/png", SMALL_IMAGE_BYTES);

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

        // 카드와 같은 트랜잭션에서 음악 생성 요청이 아웃박스에 한 줄 남는다 — 재확정은 다시 쓰지 않는다
        assertThat(querySql("""
                SELECT aggregate_type, topic, payload->>'user_id', payload->>'memo', payload->>'activity_type',
                       jsonb_array_length(payload->'photos')
                FROM outbox_events WHERE aggregate_id = '%s'
                """.formatted(recordCardId)))
                .containsExactly(String.join("|", "RECORD_CARD", "test.record-card.music.requested", user.userId(),
                        "한강 한 바퀴", "DAILY", "2"));
        // 사진은 카드 순서대로, 설명이 없는 사진도 caption 키가 null로 남는다(jsonb로 저장돼도 키가 빠지지 않는다)
        assertThat(querySql("""
                SELECT photo->>'record_card_image_id', photo->>'image_key', jsonb_typeof(photo->'caption')
                FROM outbox_events, jsonb_array_elements(payload->'photos') WITH ORDINALITY AS p(photo, n)
                WHERE aggregate_id = '%s' ORDER BY n
                """.formatted(recordCardId)))
                .containsExactly(
                        String.join("|", (String) uploads.get(0).get("recordCardImageId"), firstKey, "string"),
                        String.join("|", (String) uploads.get(1).get("recordCardImageId"), secondKey, "null"));
        // 기록 요약은 running_records 값 그대로고, 시작 시각만 +09:00이 붙는다
        assertThat(querySql("""
                SELECT (s->>'total_distance')::int = r.total_distance, (s->>'total_duration')::int = r.total_duration,
                       (s->>'avg_pace')::int = r.avg_pace, s ? 'avg_cadence', s ? 'total_elevation_gain',
                       s->>'start_at' ~ '^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00$'
                FROM outbox_events o, jsonb_extract_path(o.payload, 'running_summary') AS s, running_records r
                WHERE o.aggregate_id = '%s' AND r.running_record_id = %d
                """.formatted(recordCardId, runningRecordId)))
                .containsExactly("t|t|t|t|t|t");
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
        // 작은 버전은 원본 크기와 상관없이 한 번에 올린다
        assertThat(upload.get("thumbnailImageKey")).isEqualTo(thumbnailKeyOf(key));
        uploadRecordCardImage(thumbnailKeyOf(key), "image/jpeg", THUMBNAIL_BYTES);

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
        uploadPhoto(key, "image/jpeg", SMALL_IMAGE_BYTES);
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
        // 동시 확정이어도 음악 요청은 한 번이다
        assertThat(querySql("SELECT count(*) FROM outbox_events WHERE aggregate_id = '"
                + created.get(0).text("recordCardId") + "'")).containsExactly("1");
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
        uploadPhoto((String) uploads.get(0).get("imageKey"), "image/jpeg", SMALL_IMAGE_BYTES);
        String completePath = completePath(draft.text("recordCardDraftId"));

        // when
        Response missing = post(completePath, Map.of(), user.accessToken());

        // then - 카드는 생기지 않는다
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.text("code")).isEqualTo("RECORD_CARD_IMAGE_NOT_UPLOADED");
        assertThat(querySql("SELECT count(*) FROM record_cards WHERE running_record_id = " + runningRecordId))
                .containsExactly("0");
        // 카드가 없으니 카드 ID는 사진 key(record-cards/{userId}/{recordCardId}/...)에서 꺼내 아웃박스를 직접 센다
        String recordCardId = ((String) uploads.get(0).get("imageKey")).split("/")[2];
        assertThat(querySql("SELECT count(*) FROM outbox_events WHERE aggregate_id = '" + recordCardId + "'"))
                .containsExactly("0");

        // 빠진 것만 올리고 다시 확정한다 — 실패한 확정이 잠금을 남기지 않았다
        uploadPhoto((String) uploads.get(1).get("imageKey"), "image/jpeg", SMALL_IMAGE_BYTES);
        Response completed = post(completePath, Map.of(), user.accessToken());
        assertThat(completed.status()).isEqualTo(201);
        // 실패한 확정은 아무것도 남기지 않았고, 성공한 확정이 한 줄을 쓴다
        assertThat(completed.text("recordCardId")).isEqualTo(recordCardId);
        assertThat(querySql("SELECT count(*) FROM outbox_events WHERE aggregate_id = '" + recordCardId + "'"))
                .containsExactly("1");
    }

    @Test
    @DisplayName("올라간 사진의 크기·형식이 작성 요청과 다르면 400 INVALID_RECORD_CARD_IMAGE다")
    void uploadedImageMismatch() {
        // given - JPEG로 요청하고 PNG로 올린다
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "a.jpg", null)), null), user.accessToken());
        uploadPhoto((String) draft.objects("uploads").get(0).get("imageKey"), "image/png",
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

    @Test
    @DisplayName("앱이 만든 스키마에도 어댑터가 이름으로 가르는 UNIQUE 제약이 운영 DDL과 같은 이름으로 있다")
    void uniqueConstraintNamesMatchProductionDdl() {
        // 어댑터는 제약 이름으로 409(기록에 카드가 이미 있음·순위 충돌)를 가른다. 이름이 다르면 같은 위반이 500이 된다 —
        // ddl-auto로 스키마를 만드는 local·test 서버에서 운영과 다르게 동작하지 않는지 본다
        assertThat(querySql("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'record_cards'::regclass AND contype = 'u'
                ORDER BY conname
                """))
                .containsExactly("uk_record_card_running_record", "uk_record_card_user_display_order");
    }

    @Test
    @DisplayName("아웃박스는 다른 환경 토픽과 모르는 대상 종류를 DB에서 막는다")
    void outboxChecksRejectWrongTopic() {
        // given - 앱이 만든 스키마(엔티티 @Check)다. 운영 DDL은 prod만 허용하도록 더 좁힌다
        String insert = """
                INSERT INTO outbox_events (outbox_event_id, aggregate_type, aggregate_id, topic, payload, created_at)
                VALUES (gen_random_uuid(), '%s', 'x', '%s', '{}'::jsonb, now());
                """;

        // when & then -> 환경 이름 오타·없는 토픽·대상 종류 오타는 저장 단계에서 실패한다
        for (String[] values : new String[][]{
                {"RECORD_CARD", "deploy.record-card.music.requested"},
                {"RECORD_CARD", "test.record-card.music.generated"},
                {"record_card", "test.record-card.music.requested"}}) {
            assertThatThrownBy(() -> executeSql(insert.formatted(values[0], values[1])))
                    .hasMessageContaining("violates check constraint");
        }
        assertThat(querySql("SELECT count(*) FROM outbox_events WHERE aggregate_id = 'x'")).containsExactly("0");
    }

    @Test
    @DisplayName("상세(8-1) — 작성자는 지표·선 아트·사진·음악 상태를 보고, 남은 공개 범위가 허락할 때만 본다")
    @SuppressWarnings("unchecked")
    void detail() {
        // given - 사진 한 장짜리 전체 공개 카드
        TestUser owner = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        long runningRecordId = finishSoloRun(owner);
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "first.jpg", "출발선")), 0), owner.accessToken());
        String imageKey = (String) draft.objects("uploads").get(0).get("imageKey");
        uploadPhoto(imageKey, "image/jpeg", SMALL_IMAGE_BYTES);
        String recordCardId = post(completePath(draft.text("recordCardDraftId")), Map.of(), owner.accessToken())
                .text("recordCardId");
        String detailPath = "/record-cards/" + recordCardId;

        // when
        Response mine = get(detailPath, owner.accessToken());

        // then - 수치는 running_records 값 그대로고, 경로는 위경도가 아닌 0~1 선 아트다
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.text("recordCardId")).isEqualTo(recordCardId);
        assertThat(mine.bool("isMine")).isTrue();
        assertThat(mine.text("visibility")).isEqualTo("PUBLIC");
        assertThat(mine.text("memo")).isEqualTo("한강 한 바퀴");
        assertThat(((Map<String, Object>) mine.body().get("author")).get("userId")).isEqualTo(owner.userId());
        Map<String, Object> record = (Map<String, Object>) mine.body().get("record");
        assertThat(querySql("""
                SELECT running_record_id, total_distance, total_duration, avg_pace
                FROM running_records WHERE running_record_id = %d
                """.formatted(runningRecordId)))
                .containsExactly(String.join("|", String.valueOf(record.get("runningRecordId")),
                        String.valueOf(record.get("totalDistanceMeters")),
                        String.valueOf(record.get("totalDurationSeconds")),
                        String.valueOf(record.get("averagePaceSecondsPerKm"))));
        List<List<List<Number>>> routeArt = (List<List<List<Number>>>) record.get("routeArt");
        assertThat(routeArt).isNotEmpty();
        assertThat(routeArt).allSatisfy(segment -> assertThat(segment).allSatisfy(point -> {
            assertThat(point).hasSize(2);
            assertThat(point.get(0).doubleValue()).isBetween(0.0, 1.0);
            assertThat(point.get(1).doubleValue()).isBetween(0.0, 1.0);
        }));
        // 북쪽으로 곧게 뛴 트랙이라 시작은 아래(y=1), 도착은 위(y=0)다
        assertThat(routeArt.getFirst().getFirst().get(1).doubleValue()).isEqualTo(1.0);
        assertThat(routeArt.getLast().getLast().get(1).doubleValue()).isEqualTo(0.0);
        // 음악은 아직 만드는 중이라 URL이 없다 — E2E는 가짜 AI를 끈다
        assertThat((Map<String, Object>) mine.body().get("baseMusic"))
                .containsEntry("status", "GENERATING").containsEntry("musicUrl", null);
        List<Map<String, Object>> images = mine.objects("images");
        assertThat(images).singleElement().satisfies(image -> {
            assertThat(image.get("caption")).isEqualTo("출발선");
            assertThat(image.get("isThumbnail")).isEqualTo(true);
            assertThat((String) image.get("imageUrl")).contains("X-Amz-Signature");
            assertThat((Map<String, Object>) image.get("music"))
                    .containsEntry("status", "GENERATING").containsEntry("musicUrl", null);
        });

        // 솔로 러닝이라 같이 뛴 사람이 없다 — 실제 DB에서 참가자 쿼리를 탄다
        assertThat(mine.objects("companions")).isEmpty();

        // 남도 전체 공개는 본다 — 내 카드가 아니다
        Response others = get(detailPath, stranger.accessToken());
        assertThat(others.status()).isEqualTo(200);
        assertThat(others.bool("isMine")).isFalse();

        // 비공개로 바뀌면 남에게는 없는 카드와 같은 404다(수정 API가 아직 없어 DB로 바꾼다)
        executeSql("UPDATE record_cards SET visibility = 'PRIVATE' WHERE record_card_id = '%s'"
                .formatted(recordCardId));
        Response hidden = get(detailPath, stranger.accessToken());
        assertThat(hidden.status()).isEqualTo(404);
        assertThat(hidden.text("code")).isEqualTo("NOT_FOUND");
        assertThat(get(detailPath, owner.accessToken()).status()).isEqualTo(200);

        // 형식이 틀린 ID는 400, 없는 카드는 404다
        assertThat(get("/record-cards/1-1-1-1-1", owner.accessToken()).status()).isEqualTo(400);
        assertThat(get("/record-cards/01a0f2d4-6180-7000-8000-4466554400c8", owner.accessToken()).status())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("삭제(9-4) — 직전 모습을 이력으로 남기고 카드·사진 행을 지운다. 지운 뒤 같은 기록으로 다시 만들 수 있다")
    void deleteCard() {
        // given - 사진 한 장짜리 카드
        TestUser owner = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        long runningRecordId = finishSoloRun(owner);
        Response draft = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "first.jpg", "출발선")), 0), owner.accessToken());
        String imageKey = (String) draft.objects("uploads").get(0).get("imageKey");
        uploadPhoto(imageKey, "image/jpeg", SMALL_IMAGE_BYTES);
        String recordCardId = post(completePath(draft.text("recordCardDraftId")), Map.of(), owner.accessToken())
                .text("recordCardId");
        String cardPath = "/record-cards/" + recordCardId;

        // 남의 카드는 지울 수 없고, 있는지도 드러나지 않는다
        Response others = delete(cardPath, stranger.accessToken());
        assertThat(others.status()).isEqualTo(404);
        assertThat(others.text("code")).isEqualTo("NOT_FOUND");

        // when
        Response deleted = delete(cardPath, owner.accessToken());

        // then - 카드·사진 행이 사라지고 상세는 404다
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(get(cardPath, owner.accessToken()).status()).isEqualTo(404);
        assertThat(querySql("SELECT count(*) FROM record_cards WHERE record_card_id = '%s'".formatted(recordCardId)))
                .containsExactly("0");
        assertThat(querySql("SELECT count(*) FROM record_card_images WHERE record_card_id = '%s'"
                .formatted(recordCardId))).containsExactly("0");
        // 지우기 직전 모습이 이력에 남는다 — 사진은 jsonb 배열, 원래 만든 시각도 복사해 둔다
        assertThat(querySql("""
                SELECT snapshot_reason, user_id, running_record_id, memo, base_music_status,
                       jsonb_array_length(images), images->0->>'imageKey', images->0->>'caption',
                       images->0->>'musicStatus', card_created_at IS NOT NULL
                FROM record_card_histories WHERE record_card_id = '%s'
                """.formatted(recordCardId)))
                .containsExactly(String.join("|", "DELETE", owner.userId(), String.valueOf(runningRecordId),
                        "한강 한 바퀴", "GENERATING", "1", imageKey, "출발선", "GENERATING", "t"));

        // 두 번 지우면 404이고 이력은 늘지 않는다
        assertThat(delete(cardPath, owner.accessToken()).status()).isEqualTo(404);
        assertThat(querySql("SELECT count(*) FROM record_card_histories WHERE record_card_id = '%s'"
                .formatted(recordCardId))).containsExactly("1");

        // 행이 없으니 같은 기록으로 새 카드(새 카드 ID)를 만들 수 있다
        String recreated = createCardWithoutImages(owner, runningRecordId);
        assertThat(recreated).isNotEqualTo(recordCardId);
    }

    @Test
    @DisplayName("이력 테이블은 스냅샷 이유를 DB에서 막는다")
    void historyChecksRejectUnknownReason() {
        assertThatThrownBy(() -> executeSql("""
                INSERT INTO record_card_histories (record_card_id, snapshot_reason, user_id, running_record_id,
                    visibility, activity_type, memo, base_music_status, images, card_created_at, created_at)
                VALUES (gen_random_uuid(), 'CREATE', gen_random_uuid(), 1, 'PUBLIC', 'DAILY', '글', 'GENERATING',
                    '[]'::jsonb, now(), now())
                """)).hasMessageContaining("ck_record_card_history_snapshot_reason");
    }

    @Test
    @DisplayName("수정(9-3) — 재료가 바뀔 때만 음악을 요청하고, 새 사진은 초안 → 업로드 → 9-2로 반영하며, 초안 뒤 다른 수정은 409다")
    @SuppressWarnings("unchecked")
    void updateCard() {
        // given - 사진 한 장(설명 "출발선") 카드
        TestUser owner = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        long runningRecordId = finishSoloRun(owner);
        Response created = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "first.jpg", "출발선")), 0), owner.accessToken());
        String firstImageId = (String) created.objects("uploads").get(0).get("recordCardImageId");
        uploadPhoto((String) created.objects("uploads").get(0).get("imageKey"), "image/jpeg",
                SMALL_IMAGE_BYTES);
        String recordCardId = post(completePath(created.text("recordCardDraftId")), Map.of(), owner.accessToken())
                .text("recordCardId");
        String cardPath = "/record-cards/" + recordCardId;
        String outboxPhotos = "SELECT jsonb_array_length(payload->'photos') FROM outbox_events WHERE aggregate_id = '%s'"
                + " ORDER BY created_at, outbox_event_id";
        String historyCount = "SELECT count(*) FROM record_card_histories WHERE record_card_id = '%s'"
                + " AND snapshot_reason = 'UPDATE'";
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("1");

        // 남의 카드는 고칠 수 없다(404), 요청 검증은 400
        assertThat(patch(cardPath, Map.of("memo", "남이 고침"), stranger.accessToken()).status()).isEqualTo(404);
        Response invalid = patch(cardPath, Map.of("memo", "  "), owner.accessToken());
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.text("message")).isEqualTo("회고 글은 비어 있을 수 없습니다.");

        // when 1 - 회고 글만 바꾼다
        Response memoOnly = patch(cardPath, Map.of("memo", "바람이 좋았던 한강"), owner.accessToken());

        // then - 바로 반영(draft null), 사진 없는 음악 요청 한 건(기본곡만), 이력 한 줄
        assertThat(memoOnly.status()).isEqualTo(200);
        assertThat(memoOnly.body().get("draft")).isNull();
        assertThat(((Map<String, Object>) memoOnly.body().get("recordCard")).get("memo")).isEqualTo("바람이 좋았던 한강");
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("1", "0");
        assertThat(querySql(historyCount.formatted(recordCardId))).containsExactly("1");

        // when 2 - 공개 범위만 바꾼다 / 같은 값으로 다시 보낸다
        assertThat(patch(cardPath, Map.of("visibility", "PRIVATE"), owner.accessToken()).status()).isEqualTo(200);
        assertThat(patch(cardPath, Map.of("visibility", "PRIVATE", "images",
                List.of(Map.of("recordCardImageId", firstImageId, "caption", "출발선"))), owner.accessToken())
                .status()).isEqualTo(200);

        // then - 공개 범위는 이력만 남기고 음악은 요청하지 않는다. 같은 값은 아무것도 남기지 않는다
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("1", "0");
        assertThat(querySql(historyCount.formatted(recordCardId))).containsExactly("2");

        // when 3 - 기존 사진 설명을 바꾸고 새 사진을 넣는다
        Response withNew = patch(cardPath, Map.of("images", List.of(
                Map.of("recordCardImageId", firstImageId, "caption", "다리 밑 노을"),
                Map.of("mimeType", "image/png", "fileSizeBytes", SMALL_IMAGE_BYTES, "thumbnailFileSizeBytes",
                        THUMBNAIL_BYTES, "fileName", "second.png", "caption", "결승선"))), owner.accessToken());

        // then - 아직 반영하지 않는다: draft와 지금(수정 전) 카드
        assertThat(withNew.status()).isEqualTo(200);
        Map<String, Object> draft = (Map<String, Object>) withNew.body().get("draft");
        List<Map<String, Object>> uploads = (List<Map<String, Object>>) draft.get("uploads");
        assertThat(uploads).hasSize(1);
        assertThat(((List<?>) ((Map<String, Object>) withNew.body().get("recordCard")).get("images"))).hasSize(1);
        assertThat(querySql("SELECT count(*) FROM record_card_images WHERE record_card_id = '%s'"
                .formatted(recordCardId))).containsExactly("1");

        // when - 올리고 9-2로 확정한다(작성과 같은 API)
        String newImageId = (String) uploads.get(0).get("recordCardImageId");
        uploadPhoto((String) uploads.get(0).get("imageKey"), "image/png", SMALL_IMAGE_BYTES);
        Response confirmed = post(completePath((String) draft.get("recordCardDraftId")), Map.of(),
                owner.accessToken());

        // then - 같은 카드가 고쳐지고, 설명이 바뀐 사진과 새 사진만 음악을 요청한다
        assertThat(confirmed.status()).isEqualTo(201);
        assertThat(confirmed.text("recordCardId")).isEqualTo(recordCardId);
        assertThat(querySql("""
                SELECT record_card_image_id, sort_order, coalesce(caption, '-'), music_status
                FROM record_card_images WHERE record_card_id = '%s' ORDER BY sort_order
                """.formatted(recordCardId))).containsExactly(
                String.join("|", firstImageId, "0", "다리 밑 노을", "GENERATING"),
                String.join("|", newImageId, "1", "결승선", "GENERATING"));
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("1", "0", "2");
        assertThat(querySql(historyCount.formatted(recordCardId))).containsExactly("3");

        // when 4 - 새 사진 초안을 만든 뒤, 확정 전에 회고 글을 바로 고친다
        Response pending = patch(cardPath, Map.of("images", List.of(
                Map.of("recordCardImageId", firstImageId, "caption", "다리 밑 노을"),
                Map.of("recordCardImageId", newImageId, "caption", "결승선"),
                Map.of("mimeType", "image/jpeg", "fileSizeBytes", SMALL_IMAGE_BYTES, "thumbnailFileSizeBytes",
                        THUMBNAIL_BYTES, "fileName", "third.jpg"))),
                owner.accessToken());
        Map<String, Object> pendingDraft = (Map<String, Object>) pending.body().get("draft");
        uploadPhoto((String) ((List<Map<String, Object>>) pendingDraft.get("uploads")).get(0).get("imageKey"),
                "image/jpeg", SMALL_IMAGE_BYTES);
        assertThat(patch(cardPath, Map.of("memo", "다른 기기에서 고친 회고"), owner.accessToken()).status())
                .isEqualTo(200);
        Response conflict = post(completePath((String) pendingDraft.get("recordCardDraftId")), Map.of(),
                owner.accessToken());

        // then - 409이고, 끼어든 수정은 그대로 남는다
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.text("code")).isEqualTo("RECORD_CARD_CHANGED");
        assertThat(querySql("SELECT memo FROM record_cards WHERE record_card_id = '%s'".formatted(recordCardId)))
                .containsExactly("다른 기기에서 고친 회고");
        assertThat(querySql("SELECT count(*) FROM record_card_images WHERE record_card_id = '%s'"
                .formatted(recordCardId))).containsExactly("2");
    }

    @Test
    @DisplayName("음악 다시 만들기(9-6) — 204. 실패한 음악만 생성 중으로 되돌리고 그것만 요청한다(완료된 곡은 그대로). 없으면 아무것도 하지 않는다")
    @SuppressWarnings("unchecked")
    void retryMusic() {
        // given - 사진 두 장 카드. E2E는 AI가 없어 음악 결과를 DB에 직접 넣는다(기본곡·첫 사진 완료, 둘째 사진 실패)
        TestUser owner = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        long runningRecordId = finishSoloRun(owner);
        Response created = post("/record-cards", draftRequest(runningRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "first.jpg", "출발선"),
                image("image/png", SMALL_IMAGE_BYTES, "second.png", null)), 0), owner.accessToken());
        List<Map<String, Object>> uploads = created.objects("uploads");
        uploadPhoto((String) uploads.get(0).get("imageKey"), "image/jpeg", SMALL_IMAGE_BYTES);
        uploadPhoto((String) uploads.get(1).get("imageKey"), "image/png", SMALL_IMAGE_BYTES);
        String recordCardId = post(completePath(created.text("recordCardDraftId")), Map.of(), owner.accessToken())
                .text("recordCardId");
        String retryPath = "/record-cards/" + recordCardId + "/retry";
        String outboxPhotos = "SELECT jsonb_array_length(payload->'photos') FROM outbox_events WHERE aggregate_id = '%s'"
                + " ORDER BY created_at, outbox_event_id";
        String musicStates = """
                SELECT 'base', base_music_status, coalesce(base_music_key, '-') FROM record_cards
                WHERE record_card_id = '%1$s'
                UNION ALL
                SELECT * FROM (SELECT 'image', music_status, coalesce(music_key, '-') FROM record_card_images
                WHERE record_card_id = '%1$s' ORDER BY sort_order) images
                """;
        executeSql("UPDATE record_cards SET base_music_status = 'COMPLETED', base_music_key = 'audio/base.wav'"
                + " WHERE record_card_id = '%s'".formatted(recordCardId));
        executeSql("UPDATE record_card_images SET music_status = 'COMPLETED', music_key = 'audio/1.wav'"
                + " WHERE record_card_id = '%s' AND sort_order = 0".formatted(recordCardId));
        executeSql("UPDATE record_card_images SET music_status = 'FAILED'"
                + " WHERE record_card_id = '%s' AND sort_order = 1".formatted(recordCardId));
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("2");

        // 남의 카드는 404, 형식이 틀린 ID는 400 — 둘 다 아무것도 바꾸지 않는다
        Response others = post(retryPath, Map.of(), stranger.accessToken());
        assertThat(others.status()).isEqualTo(404);
        assertThat(others.text("code")).isEqualTo("NOT_FOUND");
        assertThat(post("/record-cards/not-a-uuid/retry", Map.of(), owner.accessToken()).status()).isEqualTo(400);
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("2");

        // when
        Response retried = post(retryPath, Map.of(), owner.accessToken());

        // then - 204 본문 없음. 실패한 둘째 사진만 key 없이 GENERATING이고, 완료된 기본곡·첫 사진은 key까지 그대로다
        assertThat(retried.status()).isEqualTo(204);
        Response detail = get("/record-cards/" + recordCardId, owner.accessToken());
        assertThat(((Map<String, Object>) detail.body().get("baseMusic")).get("status")).isEqualTo("COMPLETED");
        assertThat((List<Map<String, Object>>) detail.body().get("images"))
                .extracting(image -> ((Map<String, Object>) image.get("music")).get("status"))
                .containsExactly("COMPLETED", "GENERATING");
        assertThat(querySql(musicStates.formatted(recordCardId))).containsExactly(
                "base|COMPLETED|audio/base.wav", "image|COMPLETED|audio/1.wav", "image|GENERATING|-");
        // 실패한 사진 한 장만 실은 요청이 한 건 더 나가고, 이력은 남기지 않는다
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("2", "1");
        assertThat(querySql("SELECT count(*) FROM record_card_histories WHERE record_card_id = '%s'"
                .formatted(recordCardId))).containsExactly("0");

        // when - 실패한 음악이 없는 채로 다시 부른다(두 번 누름)
        Response again = post(retryPath, Map.of(), owner.accessToken());

        // then - 같은 204이고 요청은 늘지 않는다
        assertThat(again.status()).isEqualTo(204);
        assertThat(querySql(outboxPhotos.formatted(recordCardId))).containsExactly("2", "1");
    }

    @Test
    @DisplayName("기록 목록(7-1) — 기록마다 붙은 카드 요약을 싣는다: 활동 유형과 함께 사진 있는 카드는 썸네일 URL, 사진 없는 카드는 선 아트(50점 이하), 카드 없는 기록은 null")
    @SuppressWarnings("unchecked")
    void runningRecordsCarryCardSummary() {
        // given - 같은 날 세 번 뛰고, 첫째는 사진 카드(RACE), 둘째는 사진 없는 카드(DAILY), 셋째는 카드 없음. 첫째는 비공개다
        TestUser owner = signUpAndOnboard();
        long photoRecordId = finishSoloRun(owner);
        long lineRecordId = finishSoloRun(owner);
        long bareRecordId = finishSoloRun(owner);
        Map<String, Object> photoRequest = new HashMap<>(draftRequest(photoRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "first.jpg", "출발선")), 0));
        photoRequest.put("visibility", "PRIVATE");
        photoRequest.put("activityType", "RACE");
        Response photoDraft = post("/record-cards", photoRequest, owner.accessToken());
        uploadPhoto((String) photoDraft.objects("uploads").get(0).get("imageKey"), "image/jpeg",
                SMALL_IMAGE_BYTES);
        String photoCardId = post(completePath(photoDraft.text("recordCardDraftId")), Map.of(), owner.accessToken())
                .text("recordCardId");
        String lineCardId = createCardWithoutImages(owner, lineRecordId);

        // when
        String day = trackStartAt.toLocalDate().toString();
        Response records = get("/users/me/running-records?from=" + day + "&to=" + day, owner.accessToken());

        // then - 순서·기존 필드는 그대로이고 기록마다 recordCard가 붙는다
        assertThat(records.status()).isEqualTo(200);
        List<Map<String, Object>> items = records.objects("runningRecords");
        assertThat(items).extracting(item -> ((Number) item.get("runningRecordId")).longValue())
                .containsExactly(photoRecordId, lineRecordId, bareRecordId);

        Map<String, Object> photoCard = (Map<String, Object>) items.get(0).get("recordCard");
        assertThat(photoCard.get("recordCardId")).isEqualTo(photoCardId);
        assertThat(photoCard.get("activityType")).isEqualTo("RACE");
        assertThat((String) photoCard.get("thumbnailUrl")).startsWith("http").contains("/thumbnail/");
        assertThat(photoCard).containsEntry("routeArt", null);

        Map<String, Object> lineCard = (Map<String, Object>) items.get(1).get("recordCard");
        assertThat(lineCard.get("recordCardId")).isEqualTo(lineCardId);
        assertThat(lineCard.get("activityType")).isEqualTo("DAILY");
        assertThat(lineCard).containsEntry("thumbnailUrl", null);
        List<List<List<Number>>> routeArt = (List<List<List<Number>>>) lineCard.get("routeArt");
        assertThat(routeArt).isNotEmpty();
        assertThat(routeArt.stream().mapToInt(List::size).sum()).isBetween(2, 50);
        assertThat(routeArt).allSatisfy(segment -> assertThat(segment).allSatisfy(point -> {
            assertThat(point).hasSize(2);
            assertThat(point.get(0).doubleValue()).isBetween(0.0, 1.0);
            assertThat(point.get(1).doubleValue()).isBetween(0.0, 1.0);
        }));

        assertThat(items.get(2)).containsKey("recordCard").containsEntry("recordCard", null);
    }

    @Test
    @DisplayName("작은 버전(썸네일)을 올리지 않으면 확정은 400이고, 작은 버전을 올리면 같은 초안으로 확정된다 — 회고 글은 1000자까지 저장된다")
    void thumbnailRequiredAndLongMemo() {
        // given - 회고 글 1000자, 원본만 올린다
        TestUser user = signUpAndOnboard();
        long runningRecordId = finishSoloRun(user);
        Map<String, Object> request = new HashMap<>(draftRequest(runningRecordId, List.of(
                image("image/png", SMALL_IMAGE_BYTES, "한강 야경.png", null)), 0));
        request.put("memo", "가".repeat(1000));
        Response draft = post("/record-cards", request, user.accessToken());
        assertThat(draft.status()).isEqualTo(200);
        Map<String, Object> upload = draft.objects("uploads").get(0);
        String imageKey = (String) upload.get("imageKey");
        assertThat(upload.get("thumbnailImageKey")).isEqualTo(thumbnailKeyOf(imageKey));
        assertThat((String) upload.get("thumbnailUploadUrl")).startsWith("http");
        uploadRecordCardImage(imageKey, "image/png", SMALL_IMAGE_BYTES);
        String completePath = completePath(draft.text("recordCardDraftId"));

        // when & then - 작은 버전이 없어 400, 카드는 없다
        Response missing = post(completePath, Map.of(), user.accessToken());
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.text("code")).isEqualTo("RECORD_CARD_IMAGE_NOT_UPLOADED");
        assertThat(querySql("SELECT count(*) FROM record_cards WHERE running_record_id = " + runningRecordId))
                .containsExactly("0");

        // when & then - 작은 버전을 올리면 확정되고, 1000자 회고 글이 DB CHECK를 통과한다
        uploadRecordCardImage(thumbnailKeyOf(imageKey), "image/png", THUMBNAIL_BYTES);
        Response completed = post(completePath, Map.of(), user.accessToken());
        assertThat(completed.status()).isEqualTo(201);
        assertThat(querySql("SELECT char_length(memo) FROM record_cards WHERE record_card_id = '%s'"
                .formatted(completed.text("recordCardId")))).containsExactly("1000");

        // 1000자를 넘는 회고 글과 1MB를 넘는 작은 버전은 400이다
        long otherRecordId = finishSoloRun(user);
        Map<String, Object> tooLong = new HashMap<>(draftRequest(otherRecordId, List.of(), null));
        tooLong.put("memo", "가".repeat(1001));
        assertThat(post("/record-cards", tooLong, user.accessToken()).text("message"))
                .isEqualTo("회고 글은 1000자 이하여야 합니다.");
        Map<String, Object> bigThumbnail = new HashMap<>(image("image/jpeg", SMALL_IMAGE_BYTES, "a.jpg", null));
        bigThumbnail.put("thumbnailFileSizeBytes", 1024 * 1024 + 1);
        Response tooBig = post("/record-cards", draftRequest(otherRecordId, List.of(bigThumbnail), 0),
                user.accessToken());
        assertThat(tooBig.status()).isEqualTo(400);
        assertThat(tooBig.text("message")).isEqualTo("썸네일은 1MB 이하만 업로드할 수 있습니다.");
    }

    @Test
    @DisplayName("기록 카드 목록(10-3) — 순위 역순, 본인은 비공개까지·남은 공개만, 커서로 이어 읽고, 썸네일은 작은 버전이다")
    void listCards() {
        // given - 세 장: 첫째 공개(사진), 둘째 비공개(사진 없음), 셋째 친구 공개(사진 없음)
        TestUser owner = signUpAndOnboard();
        TestUser stranger = signUpAndOnboard();
        long firstRecordId = finishSoloRun(owner);
        long secondRecordId = finishSoloRun(owner);
        long thirdRecordId = finishSoloRun(owner);
        Response draft = post("/record-cards", draftRequest(firstRecordId, List.of(
                image("image/jpeg", SMALL_IMAGE_BYTES, "first.jpg", null)), 0), owner.accessToken());
        uploadPhoto((String) draft.objects("uploads").get(0).get("imageKey"), "image/jpeg", SMALL_IMAGE_BYTES);
        String first = post(completePath(draft.text("recordCardDraftId")), Map.of(), owner.accessToken())
                .text("recordCardId");
        String second = createCardWithoutImages(owner, secondRecordId);
        String third = createCardWithoutImages(owner, thirdRecordId);
        executeSql("UPDATE record_cards SET visibility = 'PRIVATE' WHERE record_card_id = '%s'".formatted(second));
        executeSql("UPDATE record_cards SET visibility = 'FRIENDS' WHERE record_card_id = '%s'".formatted(third));
        String listPath = "/users/" + owner.userId() + "/record-cards";

        // when - 본인이 두 장씩 읽는다
        Response page1 = get(listPath + "?limit=2", owner.accessToken());
        Response page2 = get(listPath + "?limit=2&cursor=" + page1.text("nextCursor"), owner.accessToken());

        // then - 최신(순위가 큰 것)이 위다. 비공개도 보인다
        assertThat(page1.status()).isEqualTo(200);
        assertThat(page1.objects("items")).extracting(item -> item.get("recordCardId"))
                .containsExactly(third, second);
        assertThat(page2.objects("items")).extracting(item -> item.get("recordCardId")).containsExactly(first);
        assertThat(page2.body().get("nextCursor")).isNull();
        Map<String, Object> photoCard = page2.objects("items").getFirst();
        assertThat((String) photoCard.get("thumbnailUrl")).contains("/thumbnail/first.jpg");
        assertThat(photoCard.get("imageCount")).isEqualTo(1);
        assertThat(page1.objects("items").getFirst()).containsEntry("thumbnailUrl", null)
                .containsEntry("imageCount", 0);

        // 남은 공개 카드만 본다
        Response others = get(listPath, stranger.accessToken());
        assertThat(others.objects("items")).extracting(item -> item.get("recordCardId")).containsExactly(first);

        // 깨진 커서·형식이 틀린 사용자 ID는 400, 없는 사용자는 404다
        assertThat(get(listPath + "?cursor=broken!", owner.accessToken()).status()).isEqualTo(400);
        assertThat(get("/users/not-a-uuid/record-cards", owner.accessToken()).status()).isEqualTo(400);
        assertThat(get("/users/01a0f2d4-6180-7000-8000-446655440099/record-cards", owner.accessToken()).status())
                .isEqualTo(404);
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

    // 앱처럼 원본과 작은 버전(썸네일)을 함께 올린다 — 작은 버전은 원본과 같은 형식이다
    private void uploadPhoto(String imageKey, String contentType, int sizeBytes) {
        uploadRecordCardImage(imageKey, contentType, sizeBytes);
        uploadRecordCardImage(thumbnailKeyOf(imageKey), contentType, THUMBNAIL_BYTES);
    }

    // 작은 버전 key — 같은 사진 폴더의 thumbnail/ 아래에 원본 파일 이름 그대로(api-spec 9-1)
    private static String thumbnailKeyOf(String imageKey) {
        int lastSlash = imageKey.lastIndexOf('/');
        return imageKey.substring(0, lastSlash + 1) + "thumbnail/" + imageKey.substring(lastSlash + 1);
    }

    private static Map<String, Object> image(String mimeType, long fileSizeBytes, String fileName, String caption) {
        Map<String, Object> image = new HashMap<>();
        image.put("mimeType", mimeType);
        image.put("fileSizeBytes", fileSizeBytes);
        image.put("thumbnailFileSizeBytes", THUMBNAIL_BYTES);
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
