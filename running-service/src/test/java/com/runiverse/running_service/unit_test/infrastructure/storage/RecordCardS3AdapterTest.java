package com.runiverse.running_service.unit_test.infrastructure.storage;

import com.runiverse.running_service.application.common.port.out.UploadedImage;
import com.runiverse.running_service.application.recordcard.port.out.CompleteMultipartUploadPort.MultipartCompletion;
import com.runiverse.running_service.application.recordcard.port.out.MultipartUpload;
import com.runiverse.running_service.infrastructure.storage.S3Properties;
import com.runiverse.running_service.infrastructure.storage.RecordCardS3Adapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.paginators.ListPartsIterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 서명은 실제 presigner로 만들고(네트워크 없음), 서명에 넣은 요청은 가로채 크기를 본다 — URL에는 서명한 헤더 이름만 보인다
@SuppressWarnings("unchecked")
@DisplayName("기록 카드 S3 어댑터 단위 테스트")
class RecordCardS3AdapterTest {

    private static final String REGION = "ap-northeast-2";
    private static final String USER_ASSET_BUCKET = "runiverse-user-assets-test";
    private static final String GPS_TRACK_BUCKET = "runiverse-gps-tracks-test";
    private static final String RECORD_CARD_BUCKET = "runiverse-record-cards-test";
    private static final String KEY = "record-cards/u/c/i/image.jpg";
    private static final long PART_SIZE = 10L * 1024 * 1024;
    private static final Duration URL_TTL = Duration.ofMinutes(30);

    private S3Presigner realPresigner;
    private S3Client s3Client;
    private RecordCardS3Adapter adapter;
    private final List<UploadPartRequest> signedParts = new ArrayList<>();
    private CreateMultipartUploadRequest createRequest;
    private Duration signedTtl;

    @BeforeEach
    void setUp() {
        realPresigner = S3Presigner.builder()
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIATESTTESTTESTTEST", "test-secret-key")))
                .build();
        S3Presigner presigner = mock(S3Presigner.class);
        when(presigner.presignUploadPart(any(Consumer.class))).thenAnswer(invocation -> {
            UploadPartPresignRequest.Builder builder = UploadPartPresignRequest.builder();
            ((Consumer<UploadPartPresignRequest.Builder>) invocation.getArgument(0)).accept(builder);
            UploadPartPresignRequest request = builder.build();
            signedParts.add(request.uploadPartRequest());
            signedTtl = request.signatureDuration();
            return realPresigner.presignUploadPart(request);
        });

        when(presigner.presignPutObject(any(Consumer.class))).thenAnswer(invocation -> {
            PutObjectPresignRequest.Builder builder = PutObjectPresignRequest.builder();
            ((Consumer<PutObjectPresignRequest.Builder>) invocation.getArgument(0)).accept(builder);
            return realPresigner.presignPutObject(builder.build());
        });

        s3Client = mock(S3Client.class);
        when(s3Client.createMultipartUpload(any(Consumer.class))).thenAnswer(invocation -> {
            CreateMultipartUploadRequest.Builder builder = CreateMultipartUploadRequest.builder();
            ((Consumer<CreateMultipartUploadRequest.Builder>) invocation.getArgument(0)).accept(builder);
            createRequest = builder.build();
            return CreateMultipartUploadResponse.builder().uploadId("s3-upload-id").build();
        });

        adapter = new RecordCardS3Adapter(presigner, new S3Properties(REGION, USER_ASSET_BUCKET, GPS_TRACK_BUCKET, RECORD_CARD_BUCKET,
                Duration.ofMinutes(10), Duration.ofHours(1), null, null), s3Client);
    }

    @AfterEach
    void tearDown() {
        realPresigner.close();
    }

    @Test
    @DisplayName("기록 카드 버킷에 형식을 넣어 시작하고, S3가 준 UploadId를 돌려준다")
    void startWithContentType() {
        // when
        MultipartUpload upload = adapter.start(KEY, "image/png", PART_SIZE + 1, PART_SIZE, URL_TTL);

        // then -> 조각에는 Content-Type이 없어 합친 사진의 형식은 시작 요청이 정한다
        assertThat(upload.uploadId()).isEqualTo("s3-upload-id");
        assertThat(createRequest.bucket()).isEqualTo(RECORD_CARD_BUCKET);
        assertThat(createRequest.key()).isEqualTo(KEY);
        assertThat(createRequest.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("조각 수는 크기를 조각 크기로 나눠 올림하고, 마지막 조각만 작게 서명한다")
    void splitIntoParts() {
        // given
        long size = PART_SIZE * 2 + 123;

        // when
        MultipartUpload upload = adapter.start(KEY, "image/jpeg", size, PART_SIZE, URL_TTL);

        // then
        assertThat(upload.parts()).extracting(MultipartUpload.Part::partNumber).containsExactly(1, 2, 3);
        assertThat(signedParts).extracting(UploadPartRequest::contentLength)
                .containsExactly(PART_SIZE, PART_SIZE, 123L);
        assertThat(signedParts).allSatisfy(part -> {
            assertThat(part.bucket()).isEqualTo(RECORD_CARD_BUCKET);
            assertThat(part.key()).isEqualTo(KEY);
            assertThat(part.uploadId()).isEqualTo("s3-upload-id");
        });
    }

    @Test
    @DisplayName("크기가 조각 크기로 딱 나눠지면 마지막 조각도 같은 크기다")
    void exactMultiple() {
        // when
        MultipartUpload upload = adapter.start(KEY, "image/jpeg", PART_SIZE * 2, PART_SIZE, URL_TTL);

        // then
        assertThat(upload.parts()).hasSize(2);
        assertThat(signedParts).extracting(UploadPartRequest::contentLength).containsExactly(PART_SIZE, PART_SIZE);
    }

    @Test
    @DisplayName("조각 URL은 조각 번호·UploadId를 담고, 크기를 서명하며, 받은 유효 시간을 따른다")
    void partUrlsAreSigned() {
        // when
        MultipartUpload upload = adapter.start(KEY, "image/jpeg", PART_SIZE + 1, PART_SIZE, URL_TTL);

        // then -> 크기를 서명해야 앱이 다르게 자르면 S3가 403으로 막는다
        String second = upload.parts().get(1).uploadUrl();
        assertThat(second).startsWith("https://%s.s3.%s.amazonaws.com/%s?".formatted(RECORD_CARD_BUCKET, REGION, KEY));
        assertThat(queryParam(second, "partNumber")).isEqualTo("2");
        assertThat(queryParam(second, "uploadId")).isEqualTo("s3-upload-id");
        assertThat(queryParam(second, "X-Amz-SignedHeaders")).contains("content-length");
        assertThat(queryParam(second, "X-Amz-Expires")).isEqualTo(String.valueOf(URL_TTL.toSeconds()));
        assertThat(signedTtl).isEqualTo(URL_TTL);
    }

    @Test
    @DisplayName("한 번에 올리는 URL은 기록 카드 버킷을 가리키고, 형식·크기를 서명하며, 받은 유효 시간(초안과 같다)을 따른다")
    void singleUploadUrl() {
        // when
        String url = adapter.generate(KEY, "image/jpeg", 20_480L, URL_TTL);

        // then -> 프로필 사진 버킷(user-assets)으로 가면 AI 서버가 읽지 못한다
        assertThat(url).startsWith("https://%s.s3.%s.amazonaws.com/%s?".formatted(RECORD_CARD_BUCKET, REGION, KEY));
        assertThat(queryParam(url, "X-Amz-SignedHeaders")).contains("content-type").contains("content-length");
        assertThat(queryParam(url, "X-Amz-Expires")).isEqualTo(String.valueOf(URL_TTL.toSeconds()));
    }

    @Test
    @DisplayName("HEAD로 기록 카드 버킷의 사진 크기·형식을 읽는다")
    void loadImage() {
        // given
        List<HeadObjectRequest> heads = new ArrayList<>();
        when(s3Client.headObject(any(Consumer.class))).thenAnswer(invocation -> {
            HeadObjectRequest.Builder builder = HeadObjectRequest.builder();
            ((Consumer<HeadObjectRequest.Builder>) invocation.getArgument(0)).accept(builder);
            heads.add(builder.build());
            return HeadObjectResponse.builder().contentLength(20_480L).contentType("image/jpeg").build();
        });

        // when & then
        assertThat(adapter.load(KEY)).contains(new UploadedImage(20_480L, "image/jpeg"));
        assertThat(heads.getFirst().bucket()).isEqualTo(RECORD_CARD_BUCKET);
        assertThat(heads.getFirst().key()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("사진이 없으면 NoSuchKey든 404 상태든 비어 있고, 다른 S3 오류는 던진다")
    void loadMissingImage() {
        // given
        when(s3Client.headObject(any(Consumer.class)))
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenThrow(S3Exception.builder().statusCode(403).build());

        // when & then
        assertThat(adapter.load(KEY)).isEmpty();
        assertThat(adapter.load(KEY)).isEmpty();
        assertThatThrownBy(() -> adapter.load(KEY)).isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("조각이 다 있으면 번호순으로 ETag를 모아 합친다")
    void completeWhenAllPartsUploaded() {
        // given -> 목록이 번호순이 아니어도 합칠 때는 번호순이어야 한다
        givenParts(List.of(part(2, "e2"), part(1, "e1"), part(3, "e3")));
        List<CompleteMultipartUploadRequest> completes = new ArrayList<>();
        when(s3Client.completeMultipartUpload(any(Consumer.class))).thenAnswer(invocation -> {
            CompleteMultipartUploadRequest.Builder builder = CompleteMultipartUploadRequest.builder();
            ((Consumer<CompleteMultipartUploadRequest.Builder>) invocation.getArgument(0)).accept(builder);
            completes.add(builder.build());
            return null;
        });

        // when
        MultipartCompletion result = adapter.complete(KEY, "s3-upload-id", 3);

        // then
        assertThat(result).isEqualTo(MultipartCompletion.COMPLETED);
        CompleteMultipartUploadRequest complete = completes.getFirst();
        assertThat(complete.bucket()).isEqualTo(RECORD_CARD_BUCKET);
        assertThat(complete.key()).isEqualTo(KEY);
        assertThat(complete.uploadId()).isEqualTo("s3-upload-id");
        assertThat(complete.multipartUpload().parts())
                .extracting(CompletedPart::partNumber, CompletedPart::eTag)
                .containsExactly(org.assertj.core.api.Assertions.tuple(1, "e1"),
                        org.assertj.core.api.Assertions.tuple(2, "e2"),
                        org.assertj.core.api.Assertions.tuple(3, "e3"));
    }

    @Test
    @DisplayName("조각이 덜 올라왔으면 합치지 않고 빠졌다고 알린다")
    void partsMissing() {
        // given
        givenParts(List.of(part(1, "e1")));

        // when & then
        assertThat(adapter.complete(KEY, "s3-upload-id", 3)).isEqualTo(MultipartCompletion.PARTS_MISSING);
        verify(s3Client, never()).completeMultipartUpload(any(Consumer.class));
    }

    @Test
    @DisplayName("UploadId가 S3에 없으면(이미 합쳤거나 정리됐다) 그렇다고 알린다 — HEAD로 가를 몫이다")
    void uploadNotFound() {
        // given
        when(s3Client.listPartsPaginator(any(Consumer.class))).thenThrow(NoSuchUploadException.builder().build());

        // when & then
        assertThat(adapter.complete(KEY, "s3-upload-id", 3)).isEqualTo(MultipartCompletion.UPLOAD_NOT_FOUND);
    }

    @Test
    @DisplayName("조각을 확인한 뒤 합치는 사이 다른 요청이 먼저 합쳤어도 같은 결과다")
    void uploadGoneBetweenListAndComplete() {
        // given
        givenParts(List.of(part(1, "e1")));
        when(s3Client.completeMultipartUpload(any(Consumer.class))).thenThrow(NoSuchUploadException.builder().build());

        // when & then
        assertThat(adapter.complete(KEY, "s3-upload-id", 1)).isEqualTo(MultipartCompletion.UPLOAD_NOT_FOUND);
    }

    private void givenParts(List<Part> parts) {
        ListPartsIterable iterable = mock(ListPartsIterable.class);
        SdkIterable<Part> partIterable = parts::iterator;
        when(iterable.parts()).thenReturn(partIterable);
        when(s3Client.listPartsPaginator(any(Consumer.class))).thenAnswer(invocation -> {
            ListPartsRequest.Builder builder = ListPartsRequest.builder();
            ((Consumer<ListPartsRequest.Builder>) invocation.getArgument(0)).accept(builder);
            ListPartsRequest request = builder.build();
            assertThat(request.bucket()).isEqualTo(RECORD_CARD_BUCKET);
            assertThat(request.uploadId()).isEqualTo("s3-upload-id");
            return iterable;
        });
    }

    private static Part part(int number, String eTag) {
        return Part.builder().partNumber(number).eTag(eTag).build();
    }

    private static String queryParam(String url, String name) {
        String query = url.split("\\?", 2)[1];
        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue[0].equals(name)) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("query param not found: " + name);
    }
}
