package com.runiverse.running_service.unit_test.infrastructure.storage;

import com.runiverse.running_service.application.user.port.out.UploadedImage;
import com.runiverse.running_service.infrastructure.storage.S3Properties;
import com.runiverse.running_service.infrastructure.storage.S3StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 업로드 객체 조회 어댑터 단위 테스트")
public class S3StorageAdapterLoadTest {

    private static final String REGION = "ap-northeast-2";
    // 두 버킷 값을 다르게 둬야 어댑터가 버킷을 바꿔 쓰는 실수가 테스트에 드러난다
    private static final String USER_ASSET_BUCKET = "runiverse-user-assets-test";
    private static final String GPS_TRACK_BUCKET = "runiverse-gps-tracks-test";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration VIEW_TTL = Duration.ofHours(1);
    private static final String KEY = "profiles/9f1cf1a0-0000-7000-8000-000000000001/0198a3f2.jpg";

    @Mock
    private S3Client s3Client;

    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        // 조회 경로는 presigner를 쓰지 않는다. 호출되면 NPE로 드러나도록 null을 넣는다
        adapter = new S3StorageAdapter(null,
                new S3Properties(REGION, USER_ASSET_BUCKET, GPS_TRACK_BUCKET, TTL, VIEW_TTL, null, null), s3Client);
    }

    @Test
    @DisplayName("객체가 있으면 크기와 형식을 돌려준다")
    void returnsSizeAndContentType() {
        // given
        when(s3Client.headObject(any(Consumer.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(20_480L)
                .contentType("image/jpeg")
                .build());

        // when
        Optional<UploadedImage> uploaded = adapter.load(KEY);

        // then
        assertThat(uploaded).contains(new UploadedImage(20_480L, "image/jpeg"));
    }

    @Test
    @DisplayName("객체가 없으면 비어 있는 결과를 돌려준다")
    void returnsEmptyWhenNoSuchKey() {
        // given
        when(s3Client.headObject(any(Consumer.class))).thenThrow(NoSuchKeyException.builder().build());

        // when & then -> 업로드하지 않은 요청은 400으로 이어져야 하므로 예외를 삼킨다
        assertThat(adapter.load(KEY)).isEmpty();
    }

    @Test
    @DisplayName("본문 없이 404만 오는 경우도 비어 있는 결과로 본다")
    void returnsEmptyWhenNotFoundStatus() {
        // given -> HeadObject는 응답 본문이 없어 NoSuchKeyException이 아닌 404로 올 수 있다
        when(s3Client.headObject(any(Consumer.class)))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(404).build());

        // when & then
        assertThat(adapter.load(KEY)).isEmpty();
    }

    @Test
    @DisplayName("권한 오류는 삼키지 않고 그대로 던진다")
    void propagatesAccessDenied() {
        // given
        when(s3Client.headObject(any(Consumer.class)))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(403).build());

        // when & then -> 장애를 "업로드 안 됨"으로 둔갑시키면 원인을 못 찾는다
        assertThatThrownBy(() -> adapter.load(KEY))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("설정한 버킷과 요청한 key로 조회한다")
    void looksUpConfiguredBucketAndKey() {
        // given
        when(s3Client.headObject(any(Consumer.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(1L)
                .contentType("image/png")
                .build());

        // when
        adapter.load(KEY);

        // then -> 어댑터가 넘긴 람다를 실제 빌더에 적용해 무엇을 설정했는지 꺼낸다
        HeadObjectRequest.Builder builder = HeadObjectRequest.builder();
        org.mockito.ArgumentCaptor<Consumer<HeadObjectRequest.Builder>> captor =
                org.mockito.ArgumentCaptor.forClass(Consumer.class);
        org.mockito.Mockito.verify(s3Client).headObject(captor.capture());
        captor.getValue().accept(builder);
        HeadObjectRequest request = builder.build();
        assertThat(request.bucket()).isEqualTo(USER_ASSET_BUCKET);
        assertThat(request.key()).isEqualTo(KEY);
    }
}
