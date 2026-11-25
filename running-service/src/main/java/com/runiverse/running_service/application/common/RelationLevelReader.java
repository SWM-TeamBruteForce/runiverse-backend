package com.runiverse.running_service.application.common;

import com.runiverse.running_service.application.common.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.domain.common.vo.RelationLevel;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 보는 사람이 상대에게 어떤 관계 등급인가 — 각 기능의 공개 규칙(RecordCardVisibility 등)이 이 값으로 볼 수 있는지 정한다.
// 관계 행은 행 주인 한 방향의 상태라 두 방향을 모두 읽는다 — 상대가 나를 막은 것은 상대의 행에만 있다
@Component
@RequiredArgsConstructor
public class RelationLevelReader {

    private final LoadRelationshipTypesPort loadRelationshipTypesPort;

    public RelationLevel read(UserId viewerId, UserId targetId) {
        // 1. 본인 — 관계 행을 읽지 않는다
        if (viewerId.equals(targetId)) {
            return RelationLevel.SELF;
        }

        // 2. 두 방향 — 행은 처음 요청할 때 생겨 없으면 아무 관계도 아니다(NONE)
        RelationshipType viewerToTarget = typeOf(viewerId, targetId);
        RelationshipType targetToViewer = typeOf(targetId, viewerId);

        // 3. 어느 쪽이 막았든 차단 — 친구 여부보다 먼저 본다
        if (viewerToTarget == RelationshipType.BLOCKED || targetToViewer == RelationshipType.BLOCKED) {
            return RelationLevel.BLOCKED;
        }

        // 4. 친구는 두 행이 함께 FRIEND가 된다 — 보는 사람 쪽 행으로 판단한다
        return viewerToTarget == RelationshipType.FRIEND ? RelationLevel.FRIEND : RelationLevel.OTHER;
    }

    private RelationshipType typeOf(UserId ownerId, UserId otherId) {
        return loadRelationshipTypesPort.loadTypes(ownerId, List.of(otherId.value()))
                .getOrDefault(otherId.value(), RelationshipType.NONE);
    }
}
