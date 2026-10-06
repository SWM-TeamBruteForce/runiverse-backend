package com.runiverse.running_service.unit_test.infrastructure.storage;

import com.runiverse.running_service.infrastructure.storage.S3Properties;
import com.runiverse.running_service.infrastructure.storage.S3StorageAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("S3 업로드 URL 어댑터 단위 테스트")
public class S3StorageAdapterTest {

    private static final String REGION = "ap-northeast-2";
    // 두 버킷 값을 다르게 둬야 어댑터가 버킷을 바꿔 쓰는 실수가 테스트에 드러난다
    private static final String USER_ASSET_BUCKET = "runiverse-user-assets-test";
    private static final String GPS_TRACK_BUCKET = "runiverse-gps-tracks-test";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration VIEW_TTL = Duration.ofHours(1);
    private static final String KEY = "profiles/9f1cf1a0-0000-7000-8000-000000000001/0198a3f2-0000-7000-8000" +
            "-000000000002.jpg";
    private static final String CONTENT_TYPE = "image/jpeg";
    private static final long SIZE_BYTES = 20_480L;

    private S3Presigner presigner;
    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        // 서명은 네트워크 없이 로컬에서 끝나므로 자격증명은 형식만 맞추면 된다
        presigner = S3Presigner.builder()
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIATESTTESTTESTTEST", "test-secret-key")))
                .build();
        adapter = new S3StorageAdapter(presigner,
                new S3Properties(REGION, USER_ASSET_BUCKET, GPS_TRACK_BUCKET, TTL, VIEW_TTL, null, null), null);
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    private static String queryParam(String url, String name) {
        String value = url.split("\\?", 2)[1].split(name + "=", 2)[1].split("&", 2)[0];
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("발급한 URL은 설정한 버킷의 해당 key를 가리킨다")
    void urlPointsToRequestedKey() {
        // when
        String url = adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);

        // then
        assertThat(url).startsWith("https://%s.s3.%s.amazonaws.com/%s?".formatted(USER_ASSET_BUCKET, REGION, KEY));
    }

    @Test
    @DisplayName("형식과 크기를 서명에 포함해 다른 값으로는 업로드하지 못하게 한다")
    void contentTypeAndLengthAreSigned() {
        // when
        String url = adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);

        // then -> 서명 헤더에 있어야 클라가 헤더를 바꿨을 때 S3가 403으로 막는다
        assertThat(queryParam(url, "X-Amz-SignedHeaders"))
                .contains("content-type")
                .contains("content-length");
    }

    @Test
    @DisplayName("만료 시간은 설정한 TTL을 따른다")
    void expirationFollowsConfiguredTtl() {
        // when
        String url = adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);

        // then
        assertThat(queryParam(url, "X-Amz-Expires")).isEqualTo(String.valueOf(TTL.toSeconds()));
    }

    @Test
    @DisplayName("조회 URL은 설정한 버킷의 해당 key를 가리킨다")
    void viewUrlPointsToRequestedKey() {
        // when
        String url = adapter.generate(KEY);

        // then
        assertThat(url).startsWith("https://%s.s3.%s.amazonaws.com/%s?".formatted(USER_ASSET_BUCKET, REGION, KEY));
    }

    @Test
    @DisplayName("조회 URL의 만료 시간은 업로드용과 별개인 viewUrlTtl을 따른다")
    void viewUrlExpirationFollowsViewTtl() {
        // when
        String url = adapter.generate(KEY);

        // then -> 화면에 떠 있는 동안 유효해야 하므로 업로드용(10분)보다 길다
        assertThat(queryParam(url, "X-Amz-Expires")).isEqualTo(String.valueOf(VIEW_TTL.toSeconds()));
    }

    @Test
    @DisplayName("조회 URL은 업로드 헤더를 서명에 넣지 않는다")
    void viewUrlDoesNotSignUploadHeaders() {
        // when
        String url = adapter.generate(KEY);

        // then -> GET에는 본문이 없으므로 형식·크기를 요구하면 안 된다
        assertThat(queryParam(url, "X-Amz-SignedHeaders"))
                .doesNotContain("content-type")
                .doesNotContain("content-length");
    }

    @Test
    @DisplayName("key가 다르면 서명도 달라진다")
    void signatureDependsOnKey() {
        // when
        String first = adapter.generate(KEY, CONTENT_TYPE, SIZE_BYTES);
        String second = adapter.generate("profiles/other/other.jpg", CONTENT_TYPE, SIZE_BYTES);

        // then -> 한 URL을 다른 위치에 재사용할 수 없다
        assertThat(queryParam(first, "X-Amz-Signature"))
                .isNotEqualTo(queryParam(second, "X-Amz-Signature"));
    }
}
