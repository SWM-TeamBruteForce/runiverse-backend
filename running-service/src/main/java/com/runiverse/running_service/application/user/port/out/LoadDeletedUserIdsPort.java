package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.LocalDateTime;
import java.util.List;

public interface LoadDeletedUserIdsPort {

    // 이 시각보다 먼저 탈퇴했고 아직 신원 정보가 남아 있는 기록 — S3는 프리픽스를
    // 하나씩 지정해야 지울 수 있어 일괄 UPDATE 대신 대상 목록이 필요하다
    List<UserId> loadDeletedBefore(LocalDateTime deletedBefore);
}
