package com.runiverse.e2e.match;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.MatchStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("배포 이미지 대상 초대 방 개설 E2E 테스트")
class InviteRoomE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");   // 호스트 시간대로 만들면 "오늘"이 어긋난다
    private static final int DISTANCE = 7_000;

    // 시작 시각 하한(지금 + 10분)을 넉넉히 넘긴다. 상한이 오늘 23:59라 그보다 늦으면 이 시간대엔 만들 수 없다
    private static LocalDateTime selectableStartOrSkip() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDateTime start = now.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(!start.isAfter(now.toLocalDate().atTime(23, 59)),
                "KST 23:29 이후에는 오늘 안에 고를 시작 시각이 부족하다");
        return start;
    }

    private Response open(TestUser user, LocalDateTime scheduledStartAt, int targetDistanceMeters) {
        return post("/match-rooms/invite", Map.of(
                "scheduledStartAt", scheduledStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", targetDistanceMeters), user.accessToken());
    }

    @Test
    @DisplayName("초대 방을 열면 대기 상태가 되고, 다시 열 수 없으며, 나가면 처음으로 돌아간다")
    void openWaitAndLeave() {
        // given
        TestUser host = signUpAndOnboard();
        LocalDateTime start = selectableStartOrSkip();

        // when - 1. 초대 방을 연다
        Response opened = open(host, start, DISTANCE);

        // then - 방 ID만 받고, 홈 상태는 초대 방 대기다
        assertThat(opened.status()).isEqualTo(201);
        int runningRoomId = opened.number("runningRoomId");
        Response waiting = get("/users/me/status", host.accessToken());
        assertThat(waiting.status()).isEqualTo(200);
        assertThat(waiting.text("status")).isEqualTo("WAITING");
        assertThat(waiting.text("type")).isEqualTo("INVITE");
        assertThat(waiting.number("runningRoomId")).isEqualTo(runningRoomId);
        assertThat(LocalDateTime.parse(waiting.text("scheduledStartAt"))).isEqualTo(start);
        assertThat(waiting.number("targetDistanceMeters")).isEqualTo(DISTANCE);

        // 연결 직후 스냅샷은 초대 방 전용 입장 이벤트다 — 모집 마감은 매칭 오프셋(10분)이 아니라 초대 오프셋(1분)이고,
        // 시작까지 남은 시간을 함께 싣는다
        try (MatchStream stream = openMatchStream(host.accessToken())) {
            assertThat(stream.status()).isEqualTo(200);
            Map<String, Object> snapshot = stream.await("INVITE_ROOM_ENTRANCE");
            assertThat(((Number) snapshot.get("runningRoomId")).intValue()).isEqualTo(runningRoomId);
            assertThat(snapshot.get("type")).isEqualTo("INVITE");
            assertThat(LocalDateTime.parse((String) snapshot.get("closeAt"))).isEqualTo(start.minusMinutes(1));
            assertThat(((Number) snapshot.get("startsInMs")).longValue()).isPositive();
            assertThat((List<?>) snapshot.get("invitations")).isEmpty();
        }

        // 2. 활성 신청은 방 종류와 무관하게 하나다 — 초대 방도 매칭도 더 열 수 없다
        Response again = open(host, start, DISTANCE);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.text("code")).isEqualTo("RUNNING_ALREADY_IN_PROGRESS");
        assertThat(post("/running-rooms/solo", Map.of(), host.accessToken()).status()).isEqualTo(409);

        // 3. 나가기는 매칭과 같은 경로다 — 혼자였으니 방이 닫히고 대기가 끝난다
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
        assertThat(get("/users/me/status", host.accessToken()).text("status")).isEqualTo("IDLE");
        assertThat(open(host, start, DISTANCE).status()).isEqualTo(201);
        assertThat(post("/match-rooms/leave", null, host.accessToken()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("내일 시각은 409 INVITE_START_AT_OUT_OF_RANGE로 그대로 나간다")
    void tomorrowIsOutOfRange() {
        // given
        TestUser user = signUpAndOnboard();
        LocalDateTime tomorrow = LocalDate.now(APP_ZONE).plusDays(1).atTime(7, 30);

        // when
        Response response = open(user, tomorrow, DISTANCE);

        // then -> 노출 목록에서 빠지면 500으로 가려진다
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("code")).isEqualTo("INVITE_START_AT_OUT_OF_RANGE");
        assertThat(response.text("message")).isEqualTo("선택할 수 없는 시작 시각입니다.");
        assertThat(get("/users/me/status", user.accessToken()).text("status")).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("범위 밖 거리와 초가 붙은 시각은 운영값 문구 두 문장으로 400이다")
    void invalidOptionsAreBadRequest() {
        // given
        TestUser user = signUpAndOnboard();
        LocalDateTime start = selectableStartOrSkip().withSecond(30);

        // when
        Response response = open(user, start, 500);

        // then -> 문구는 실제 설정(application.properties)으로 만들어진다
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.text("message")).isEqualTo(
                "시작 시각은 분 단위로만 선택할 수 있습니다. 목표 거리는 1000m 이상 42195m 이하여야 합니다.");
    }

    @Test
    @DisplayName("필수 값이 빠지면 Request 검증 문구로 400이다")
    void missingFieldIsBadRequest() {
        // given
        TestUser user = signUpAndOnboard();

        // when
        Response response = post("/match-rooms/invite",
                Map.of("targetDistanceMeters", DISTANCE), user.accessToken());

        // then
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.text("message")).isEqualTo("시작 시각은 필수입니다.");
    }
}
