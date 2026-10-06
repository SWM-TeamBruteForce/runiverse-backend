package com.runiverse.running_service.application.user.command.accountdeletion;

import com.runiverse.running_service.application.user.command.profileimage.ProfileImageKeyPolicy;
import com.runiverse.running_service.application.user.port.out.DeleteProfileImagesPort;
import com.runiverse.running_service.application.user.port.out.RedactDeletedUserPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeletedUserRedactor {

    private final DeleteProfileImagesPort deleteProfileImagesPort;
    private final RedactDeletedUserPort redactDeletedUserPort;

    // 건마다 커밋한다 — 배치 전체를 한 트랜잭션에 담으면 한 건이 터질 때 나머지도 되돌아간다
    @Transactional
    public void redact(UserId userId) {
        // 사진을 먼저 지운다 — 행을 먼저 비우면 다음 실행에서 걸리지 않아 사진만 영영 남는다
        deleteProfileImagesPort.deleteAllByPrefix(ProfileImageKeyPolicy.prefixOf(userId.value()));
        redactDeletedUserPort.redact(userId);
    }
}
