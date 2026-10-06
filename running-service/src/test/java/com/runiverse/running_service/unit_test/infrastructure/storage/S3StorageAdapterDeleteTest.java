package com.runiverse.running_service.unit_test.infrastructure.storage;

import com.runiverse.running_service.infrastructure.storage.S3Properties;
import com.runiverse.running_service.infrastructure.storage.S3StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 프리픽스 삭제 어댑터 단위 테스트")
public class S3StorageAdapterDeleteTest {

    private static final String REGION = "ap-northeast-2";
    // 두 버킷 값을 다르게 둬야 어댑터가 버킷을 바꿔 쓰는 실수가 테스트에 드러난다
    private static final String USER_ASSET_BUCKET = "runiverse-user-assets-test";
    private static final String GPS_TRACK_BUCKET = "runiverse-gps-tracks-test";
    private static final String PREFIX = "profiles/9f1cf1a0-0000-7000-8000-000000000001/";

    @Mock
    private S3Client s3Client;

    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        // 삭제 경로는 presigner를 쓰지 않는다. 호출되면 NPE로 드러나도록 null을 넣는다
        adapter = new S3StorageAdapter(null,
                new S3Properties(REGION, USER_ASSET_BUCKET, GPS_TRACK_BUCKET,
                        Duration.ofMinutes(10), Duration.ofHours(1), null, null), s3Client);
        // 목이 아니라 진짜 페이지네이터를 목 클라이언트 위에 올린다 — 페이지 넘김도 함께 검증된다
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(new ListObjectsV2Iterable(s3Client,
                        ListObjectsV2Request.builder().bucket(USER_ASSET_BUCKET).prefix(PREFIX).build()));
    }

    private static ListObjectsV2Response lastPage(S3Object... objects) {
        return ListObjectsV2Response.builder().contents(objects).isTruncated(false).build();
    }

    private static ListObjectsV2Response pageBefore(S3Object... objects) {
        return ListObjectsV2Response.builder().contents(objects)
                .isTruncated(true).nextContinuationToken("next").build();
    }

    private static S3Object object(String key) {
        return S3Object.builder().key(key).build();
    }

    private static DeleteObjectsRequest capturedDeleteRequest(S3Client s3Client) {
        ArgumentCaptor<Consumer<DeleteObjectsRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).deleteObjects(captor.capture());
        DeleteObjectsRequest.Builder builder = DeleteObjectsRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    @Test
    @DisplayName("설정한 버킷의 해당 프리픽스만 훑는다")
    void listsConfiguredBucketAndPrefix() {
        // given
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(lastPage());

        // when
        adapter.deleteAllByPrefix(PREFIX);

        // then -> 버킷을 바꿔 쓰면 남의 도메인 객체를 지운다
        ArgumentCaptor<ListObjectsV2Request> captor =
                ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2Paginator(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(USER_ASSET_BUCKET);
        assertThat(captor.getValue().prefix()).isEqualTo(PREFIX);
    }

    @Test
    @DisplayName("프리픽스 아래 객체를 모두 지운다")
    void deletesEveryObjectUnderPrefix() {
        // given
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(lastPage(object(PREFIX + "a.jpg"), object(PREFIX + "b.jpg")));
        when(s3Client.deleteObjects(any(Consumer.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        // when
        adapter.deleteAllByPrefix(PREFIX);

        // then
        DeleteObjectsRequest request = capturedDeleteRequest(s3Client);
        assertThat(request.bucket()).isEqualTo(USER_ASSET_BUCKET);
        assertThat(request.delete().objects()).extracting("key")
                .containsExactly(PREFIX + "a.jpg", PREFIX + "b.jpg");
    }

    @Test
    @DisplayName("목록이 여러 장이면 장마다 지운다")
    void deletesEveryPage() {
        // given -> 한 번에 1,000개까지만 오므로 그 이상이면 목록이 나뉜다
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(pageBefore(object(PREFIX + "a.jpg")), lastPage(object(PREFIX + "b.jpg")));
        when(s3Client.deleteObjects(any(Consumer.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        // when
        adapter.deleteAllByPrefix(PREFIX);

        // then
        verify(s3Client, times(2)).deleteObjects(any(Consumer.class));
    }

    @Test
    @DisplayName("지울 객체가 없으면 삭제를 부르지 않는다")
    void skipsDeleteWhenNothingToRemove() {
        // given -> 빈 목록을 넘기면 S3가 MalformedXML로 거절한다
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(lastPage());

        // when
        adapter.deleteAllByPrefix(PREFIX);

        // then
        verify(s3Client, never()).deleteObjects(any(Consumer.class));
    }

    @Test
    @DisplayName("개별 객체 삭제가 실패하면 예외로 드러낸다")
    void failsWhenAnyObjectIsNotDeleted() {
        // given -> S3는 개별 실패를 예외가 아니라 200 응답의 errors로 준다
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(lastPage(object(PREFIX + "a.jpg")));
        when(s3Client.deleteObjects(any(Consumer.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key(PREFIX + "a.jpg").code("AccessDenied").build())
                .build());

        // when & then -> 삼키면 사진이 남은 채 기록만 비워져 다음 실행에 다시 걸리지 않는다
        assertThatThrownBy(() -> adapter.deleteAllByPrefix(PREFIX))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AccessDenied");
    }

    @Test
    @DisplayName("전부 지워졌으면 예외 없이 끝난다")
    void succeedsWhenEveryObjectIsDeleted() {
        // given
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(lastPage(object(PREFIX + "a.jpg")));
        when(s3Client.deleteObjects(any(Consumer.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        // when & then
        assertThatCode(() -> adapter.deleteAllByPrefix(PREFIX)).doesNotThrowAnyException();
    }
}
