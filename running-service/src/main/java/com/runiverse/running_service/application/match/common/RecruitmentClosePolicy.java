package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 모집 마감 시각 = start_at - 방 종류별 오프셋. 컬럼에 저장하지 않으므로 계산은 여기 한 곳에서만 한다 —
// 스트림의 closeAt과 홈 상태가 서로 다른 오프셋을 쓰면 화면마다 마감이 어긋난다
@Component
@RequiredArgsConstructor
public class RecruitmentClosePolicy {

    private final MatchProperties matchProperties;
    private final InviteStartPolicy inviteStartPolicy;

    // 솔로는 모집 단계 없이 MATCHED로 태어나 이 값을 판정에 쓰지 않는다 — 매칭 오프셋으로 둔다
    public LocalDateTime closeAt(RunningRoomType type, LocalDateTime startAt) {
        return type == RunningRoomType.INVITE
                ? inviteStartPolicy.closeAt(startAt)
                : startAt.minus(matchProperties.closeOffset());
    }
}
