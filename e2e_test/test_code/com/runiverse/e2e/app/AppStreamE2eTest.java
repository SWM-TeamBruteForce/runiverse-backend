package com.runiverse.e2e.app;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 앱 스트림 E2E 테스트")
class AppStreamE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");   // 호스트 시간대로 만들면 슬롯이 어긋난다
    // 다른 매칭 테스트와 한 방에 합쳐지지 않게 그쪽이 쓰지 않는 슬롯·거리를 쓴다
    private static final LocalDateTime SLOT = LocalDate.now(APP_ZONE).plusDays(1).atTime(21, 30);
    private static final int DISTANCE = 5000;

    @Test
    @DisplayName("활성 신청이 없어도 연결된다")
    void idleUserCanConnect() {
        // given
        TestUser user = signUpAndOnboard();

        // when
        try (SseStream stream = openAppStream(user.accessToken())) {
            // then -> 매칭 스트림과 달리 거절하지 않는다
            assertThat(stream.status()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("매칭 대기 중에도 매칭 스트림과 함께 열린다")
    void waitingUserKeepsBothStreams() {
        // given
        TestUser user = signUpAndOnboard();
        Response applied = post("/match-rooms", Map.of(
                "scheduledStartAt", SLOT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE
        ), user.accessToken());
        assertThat(applied.status()).isEqualTo(201);
        int runningRoomId = applied.number("runningRoomId");

        try (SseStream appStream = openAppStream(user.accessToken());
             SseStream matchStream = openMatchStream(user.accessToken())) {
            // then -> 레지스트리가 따로라 한쪽 연결이 다른 쪽을 밀어내지 않는다
            assertThat(appStream.status()).isEqualTo(200);
            assertThat(((Number) matchStream.await("MATCH_ROOM_UPDATED").get("runningRoomId")).intValue())
                    .isEqualTo(runningRoomId);
        } finally {
            assertThat(post("/match-rooms/leave", null, user.accessToken()).status()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("같은 사용자가 다시 연결하면 이전 스트림은 서버가 닫고 새 스트림만 남는다")
    void reconnectClosesPreviousStream() {
        // given -> 앱 재시작처럼 옛 연결이 끊긴 줄 모르고 남아 있다
        TestUser user = signUpAndOnboard();

        try (SseStream first = openAppStream(user.accessToken())) {
            assertThat(first.status()).isEqualTo(200);

            // when
            try (SseStream second = openAppStream(user.accessToken())) {
                // then
                assertThat(second.status()).isEqualTo(200);
                first.awaitClosed();
            }
        }
    }
}
