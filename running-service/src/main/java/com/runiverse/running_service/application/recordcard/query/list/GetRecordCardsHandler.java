package com.runiverse.running_service.application.recordcard.query.list;

import com.runiverse.running_service.application.common.RelationLevelReader;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.recordcard.common.RecordCardStorageKeys;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardsUsecase;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardViewUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardSummariesPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSummary;
import com.runiverse.running_service.domain.common.vo.RelationLevel;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

// 10-3 기록 카드 목록 — 보는 사람의 관계 등급이 볼 수 있는 공개 범위만 순위 역순으로 준다(8-1과 같은 규칙).
// 본인은 비공개까지 전부, 친구는 공개·친구 공개, 남은 공개만. 대상이 없거나 탈퇴했거나 차단 관계면 404다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRecordCardsHandler implements GetRecordCardsUsecase {

    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final RelationLevelReader relationLevelReader;
    private final LoadRecordCardSummariesPort loadRecordCardSummariesPort;
    private final GenerateRecordCardViewUrlPort generateRecordCardViewUrlPort;

    @Override
    public GetRecordCardsResult handle(GetRecordCardsQuery query) {
        UserId viewerId = new UserId(query.viewerId());
        UserId ownerId = new UserId(query.ownerId());

        // 1. 커서부터 본다 — 깨진 커서는 400이다
        Integer before = query.cursor() == null ? null : RecordCardCursor.decode(query.cursor()).displayOrder();

        // 2. 대상 — 없거나 탈퇴했으면 404(둘을 가르지 않는다)
        if (!loadPlayerProfilesPort.loadProfiles(List.of(query.ownerId())).containsKey(query.ownerId())) {
            throw new RecordCardNotFoundException();
        }

        // 3. 관계 등급 — 차단 관계면 빈 목록이 아니라 404다(프로필 자체가 보이지 않는다)
        RelationLevel level = relationLevelReader.read(viewerId, ownerId);
        if (level == RelationLevel.BLOCKED) {
            throw new RecordCardNotFoundException();
        }
        List<RecordCardVisibility> visibilities = Arrays.stream(RecordCardVisibility.values())
                .filter(visibility -> visibility.isVisibleTo(level))
                .toList();

        // 4. 한 장보다 하나 더 읽는다 — 넘치면 다음 페이지가 있다
        List<RecordCardSummary> rows = loadRecordCardSummariesPort.loadSummaries(ownerId, visibilities, before,
                query.limit() + 1);
        boolean hasNext = rows.size() > query.limit();
        List<RecordCardSummary> page = hasNext ? rows.subList(0, query.limit()) : rows;
        String nextCursor = hasNext ? new RecordCardCursor(page.getLast().displayOrder()).encode() : null;

        return new GetRecordCardsResult(page.stream().map(this::toItem).toList(), nextCursor);
    }

    // 썸네일은 대표 사진의 작은 버전이다 — 사진 없는 카드는 null
    private GetRecordCardsResult.Item toItem(RecordCardSummary row) {
        String thumbnailUrl = row.thumbnailImageKey() == null ? null : generateRecordCardViewUrlPort.imageUrl(
                RecordCardStorageKeys.thumbnailKeyOf(row.thumbnailImageKey()));
        return new GetRecordCardsResult.Item(row.recordCardId(), thumbnailUrl, Math.toIntExact(row.imageCount()),
                row.startedAt(), row.totalDistanceMeters());
    }
}
