package com.runiverse.running_service.unit_test.infrastructure.storage;

import com.runiverse.running_service.application.running.port.out.GpsTrackUpload;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.infrastructure.storage.GpsTrackS3Adapter;
import com.runiverse.running_service.infrastructure.storage.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GPS 트랙 S3 업로드 어댑터 단위 테스트")
public class GpsTrackS3AdapterTest {

    private static final String REGION = "ap-northeast-2";
    // 두 버킷 값을 다르게 둬야 어댑터가 버킷을 바꿔 쓰는 실수가 테스트에 드러난다
    private static final String USER_ASSET_BUCKET = "runiverse-user-assets-test";
    private static final String GPS_TRACK_BUCKET = "runiverse-gps-tracks-test";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration VIEW_TTL = Duration.ofHours(1);

    private static final Long ROOM_ID = 125L;
    private static final UUID USER_ID = UUID.fromString("01a02344-364f-7d53-860a-c4f967cf1dbd");
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 27, 19, 30, 30);
    private static final String RAW =
            "[[1,37.51234,127.02345,18.4,6.2,3.12,180.5,168,312,1787654321]]";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private S3Client s3Client;

    private GpsTrackS3Adapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GpsTrackS3Adapter(s3Client, new S3Properties(
                REGION, USER_ASSET_BUCKET, GPS_TRACK_BUCKET, TTL, VIEW_TTL, null, null));
    }

    private static GpsTrackUpload upload(LocalDateTime startAt, LocalDateTime endAt) {
        return new GpsTrackUpload(ROOM_ID, USER_ID, startAt, endAt, RAW);
    }

    // 어댑터가 넘긴 람다를 실제 빌더에 적용해 무엇을 설정했는지 꺼낸다
    @SuppressWarnings("unchecked")
    private PutObjectRequest capturedRequest() {
        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private String capturedBody() throws Exception {
        ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(any(Consumer.class), captor.capture());
        return new String(captor.getValue().contentStreamProvider().newStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("키는 유저·방·시작일로 조립하고 그 값을 그대로 돌려준다")
    void buildsKeyFromUserRoomAndStartDate() {
        // when
        String key = adapter.save(upload(START_AT, END_AT));

        // then -> 반환값이 running_records.gps_track_key에 그대로 들어간다
        assertThat(key).isEqualTo(
                "gps-tracks/01a02344-364f-7d53-860a-c4f967cf1dbd/125/2026-08-27.json");
        assertThat(capturedRequest().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("자정을 넘겨 끝나도 시작일 폴더에 넣는다")
    void usesStartDateEvenWhenRunPassesMidnight() {
        // given -> 23:50에 시작해 다음 날 00:20에 끝난 러닝
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 27, 23, 50, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 8, 28, 0, 20, 0);

        // when
        String key = adapter.save(upload(startAt, endAt));

        // then -> 기록 테이블의 start_at과 같은 기준이어야 나중에 대조가 된다
        assertThat(key).contains("/2026-08-27.json");
    }

    @Test
    @DisplayName("같은 러닝을 다시 올려도 같은 키라 덮어쓴다")
    void reuploadProducesSameKey() {
        // when -> 재시도는 같은 입력으로 다시 들어온다
        String first = adapter.save(upload(START_AT, END_AT));
        String second = adapter.save(upload(START_AT, END_AT));

        // then -> 키가 흔들리면 같은 러닝의 객체가 둘 쌓이고 기록은 하나만 가리킨다
        assertThat(second).isEqualTo(first);
        verify(s3Client, times(2)).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("이미지 버킷이 아니라 GPS 트랙 버킷에 올린다")
    void uploadsToGpsTrackBucket() {
        // when
        adapter.save(upload(START_AT, END_AT));

        // then
        PutObjectRequest request = capturedRequest();
        assertThat(request.bucket()).isEqualTo(GPS_TRACK_BUCKET);
        assertThat(request.contentType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("봉투는 유효한 JSON이고 스키마와 식별자를 담는다")
    void envelopeIsValidJsonWithSchemaAndIdentifiers() throws Exception {
        // when
        adapter.save(upload(START_AT, END_AT));

        // then -> 몇 년 뒤 외부 도구가 이 객체만 보고 해석할 수 있어야 한다
        JsonNode envelope = JSON.readTree(capturedBody());
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(TrackPoint.SCHEMA_VERSION);
        assertThat(envelope.get("runningRoomId").asLong()).isEqualTo(ROOM_ID);
        assertThat(envelope.get("userId").asString()).isEqualTo(USER_ID.toString());

        List<String> fields = envelope.get("fields").valueStream()
                .map(JsonNode::asString)
                .toList();
        assertThat(fields).containsExactlyElementsOf(TrackPoint.COMPACT_FIELDS);
    }

    @Test
    @DisplayName("시각에는 오프셋이 붙고 벽시계 값은 그대로 남는다")
    void timestampsCarryOffset() throws Exception {
        // when
        adapter.save(upload(START_AT, END_AT));

        // then -> 오프셋이 없으면 외부 도구가 UTC로 오독한다.
        // 머신 타임존에 의존하지 않으려고 값을 박지 않고 성질만 확인한다
        JsonNode envelope = JSON.readTree(capturedBody());
        String startAt = envelope.get("startAt").asString();
        assertThat(startAt).matches(".*([+-]\\d{2}:\\d{2}|Z)$");
        assertThat(LocalDateTime.parse(startAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .isEqualTo(START_AT);
        assertThat(LocalDateTime.parse(envelope.get("endAt").asString(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME)).isEqualTo(END_AT);

        // 어댑터가 쓰는 기준과 같은 존이어야 한다
        assertThat(startAt).isEqualTo(
                START_AT.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @Test
    @DisplayName("raw는 파싱 없이 그대로 실린다")
    void rawIsEmbeddedVerbatim() throws Exception {
        // when
        adapter.save(upload(START_AT, END_AT));

        // then -> 왕복시키면 %.5f로 잘라둔 정밀도가 다시 흔들릴 여지가 생긴다
        String body = capturedBody();
        assertThat(body).contains("\"points\":" + RAW);

        JsonNode points = JSON.readTree(body).get("points");
        assertThat(points.size()).isEqualTo(1);
        assertThat(points.get(0).get(1).asDouble()).isEqualTo(37.51234);
    }
}
