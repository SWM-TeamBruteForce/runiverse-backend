package com.runiverse.running_service.unit_test.match.application;

import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.RecruitmentClosePolicy;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 스트림의 closeAt과 홈 상태가 이 계산 하나를 함께 쓴다 — 갈리면 화면마다 마감이 어긋난다
@DisplayName("모집 마감 시각 정책 단위 테스트")
class RecruitmentClosePolicyTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 10, 26, 19, 0);
    private static final Duration MATCH_CLOSE_OFFSET = Duration.ofMinutes(10);
    private static final Duration INVITE_CLOSE_OFFSET = Duration.ofMinutes(1);

    private final RecruitmentClosePolicy policy = policy();

    private static RecruitmentClosePolicy policy() {
        MatchProperties matchProperties = new MatchProperties(
                MATCH_CLOSE_OFFSET, Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofHours(6),
                10, Duration.ofMinutes(20),
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
        InviteProperties inviteProperties = new InviteProperties(
                Duration.ofMinutes(10), INVITE_CLOSE_OFFSET, 1_000, 42_195, 5);
        return new RecruitmentClosePolicy(matchProperties, new InviteStartPolicy(inviteProperties, matchProperties));
    }

    @Test
    @DisplayName("매칭 방은 매칭 마감 오프셋을 뺀다")
    void matchUsesMatchOffset() {
        // when & then
        assertThat(policy.closeAt(RunningRoomType.MATCH, START_AT)).isEqualTo(START_AT.minus(MATCH_CLOSE_OFFSET));
    }

    @Test
    @DisplayName("초대 방은 초대 마감 오프셋을 뺀다")
    void inviteUsesInviteOffset() {
        // when & then -> 개설 때 건 MATCH_CLOSE 예약 시각과 같아야 한다
        assertThat(policy.closeAt(RunningRoomType.INVITE, START_AT)).isEqualTo(START_AT.minus(INVITE_CLOSE_OFFSET));
    }

    @Test
    @DisplayName("솔로 방은 모집이 없어 매칭 오프셋을 그대로 쓴다")
    void soloFallsBackToMatchOffset() {
        // when & then -> 솔로는 MATCHED로 태어나 이 값으로 판정할 일이 없다
        assertThat(policy.closeAt(RunningRoomType.SOLO, START_AT)).isEqualTo(START_AT.minus(MATCH_CLOSE_OFFSET));
    }
}
