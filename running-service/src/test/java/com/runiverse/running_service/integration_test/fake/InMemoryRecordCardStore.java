package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.CreateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.DeleteRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.ExistsOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadLastRecordCardHistoryPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadMaxDisplayOrderPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadOwnRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDetailPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRunningSummaryPort;
import com.runiverse.running_service.application.recordcard.port.out.LockOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDetail;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardMusicPort;
import com.runiverse.running_service.application.recordcard.port.out.SnapshotRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.UpdateRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.MusicKey;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageCaption;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardImageKey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 카드를 붙일 기록과 그 기록의 카드 — 기록 주인·숨긴 방, 카드 ID, 작성자별 순위와 두 UNIQUE만 흉내 낸다.
// 잠금은 흉내 내지 않는다(한 스레드라 줄 설 일이 없다)
public class InMemoryRecordCardStore implements ExistsOwnRunningRecordPort, LockOwnRunningRecordPort,
        FindRecordCardIdByRunningRecordPort, LoadMaxDisplayOrderPort, CreateRecordCardPort, LoadRunningSummaryPort,
        LoadRecordCardDetailPort, LoadRecordCardForUpdatePort, LoadOwnRecordCardForUpdatePort, SnapshotRecordCardPort,
        DeleteRecordCardPort, UpdateRecordCardPort, LoadLastRecordCardHistoryPort, SaveRecordCardMusicPort {

    // 기록마다 같은 수치를 돌려준다 — 요약 값 자체는 어댑터 단위 테스트와 E2E가 본다
    public static final RunningSummary SUMMARY = new RunningSummary(5000, 1800, 360, 168,
            LocalDateTime.of(2026, 10, 19, 6, 30), 61, new BigDecimal("15.0"), 22);
    public static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 8, 0);

    private record StoredRecord(UUID userId, boolean roomHidden) {

    }

    private final Map<Long, StoredRecord> records = new HashMap<>();
    private final Map<Long, RecordCard> cardsByRecord = new HashMap<>();
    private final Map<Long, UUID> foreignCardIdsByRecord = new HashMap<>();
    private final Map<Long, List<String>> segmentPolylinesByRecord = new HashMap<>();
    private boolean staleMaxOnce;
    // 남긴 이력 — 카드 ID와 이유, 그때의 회고 글·사진 수만 본다(전체 값 매핑은 어댑터 단위 테스트가 본다)
    private final List<Snapshot> snapshots = new ArrayList<>();
    private int updateCount;
    private int musicSaveCount;

    public record Snapshot(UUID recordCardId, RecordCardSnapshotReason reason, String memo, int imageCount) {

    }

    @Override
    public boolean existsOwnedBy(RunningRecordId runningRecordId, UserId userId) {
        StoredRecord record = records.get(runningRecordId.value());
        return record != null && record.userId().equals(userId.value()) && !record.roomHidden();
    }

    @Override
    public boolean lockOwnedBy(RunningRecordId runningRecordId, UserId userId) {
        return existsOwnedBy(runningRecordId, userId);
    }

    @Override
    public Optional<RunningSummary> load(RunningRecordId runningRecordId) {
        return records.containsKey(runningRecordId.value()) ? Optional.of(SUMMARY) : Optional.empty();
    }

    @Override
    public Optional<RecordCardId> findIdByRunningRecord(RunningRecordId runningRecordId) {
        RecordCard card = cardsByRecord.get(runningRecordId.value());
        if (card != null) {
            return Optional.of(card.getRecordCardId());
        }
        return Optional.ofNullable(foreignCardIdsByRecord.get(runningRecordId.value())).map(RecordCardId::new);
    }

    @Override
    public Optional<DisplayOrder> loadMax(UserId userId) {
        if (staleMaxOnce) {
            staleMaxOnce = false;
            return Optional.empty();
        }
        return cardsByRecord.values().stream()
                .filter(card -> card.getUserId().equals(userId))
                .map(card -> card.getDisplayOrder().value())
                .max(Integer::compare)
                .map(DisplayOrder::new);
    }

    @Override
    public CreateResult create(RecordCard recordCard) {
        long recordId = recordCard.getRunningRecordId().value();
        if (cardsByRecord.containsKey(recordId) || foreignCardIdsByRecord.containsKey(recordId)) {
            return CreateResult.RECORD_ALREADY_HAS_CARD;
        }
        boolean orderTaken = cardsByRecord.values().stream()
                .anyMatch(card -> card.getUserId().equals(recordCard.getUserId())
                        && card.getDisplayOrder().equals(recordCard.getDisplayOrder()));
        if (orderTaken) {
            return CreateResult.DISPLAY_ORDER_TAKEN;
        }
        cardsByRecord.put(recordId, copy(recordCard));
        return CreateResult.CREATED;
    }

    // 상세(8-1) — 기록 수치는 SUMMARY, 구간 경로는 넣어 둔 것만 돌려준다(없으면 빈 목록)
    @Override
    public Optional<RecordCardDetail> loadDetail(RecordCardId recordCardId) {
        return cardsByRecord.entrySet().stream()
                .filter(entry -> entry.getValue().getRecordCardId().equals(recordCardId))
                .findFirst()
                .map(entry -> toDetail(entry.getKey(), entry.getValue()));
    }

    private RecordCardDetail toDetail(long runningRecordId, RecordCard card) {
        List<RecordCardDetail.Image> images = card.getImages().stream()
                .map(image -> new RecordCardDetail.Image(image.getRecordCardImageId().value(),
                        image.getImageKey().value(), image.getCaption().map(RecordCardImageCaption::value).orElse(null),
                        image.getMusicStatus(), image.getMusicKey().map(MusicKey::value).orElse(null)))
                .toList();
        return new RecordCardDetail(card.getRecordCardId().value(), card.getUserId().value(), card.getVisibility(),
                card.getActivityType(), card.getMemo().value(),
                card.getThumbnailImageKey().map(RecordCardImageKey::value).orElse(null),
                card.getBaseMusicStatus(), card.getBaseMusicKey().map(MusicKey::value).orElse(null), images,
                new RecordCardDetail.Record(runningRecordId, SUMMARY.startAt(), SUMMARY.totalDistance(),
                        SUMMARY.totalDuration(), SUMMARY.avgPace(),
                        segmentPolylinesByRecord.getOrDefault(runningRecordId, List.of())), List.of(),
                CREATED_AT, CREATED_AT);
    }

    // 잠금은 흉내 내지 않는다 — 한 스레드라 줄 설 일이 없다.
    // DB처럼 저장된 값의 사본을 준다 — 도메인에서 고친 값은 update를 불러야 저장된다
    @Override
    public Optional<RecordCard> loadForUpdate(RecordCardId recordCardId) {
        return stored(recordCardId).map(InMemoryRecordCardStore::copy);
    }

    // 남의 카드면 없는 카드와 같이 비어 있다
    @Override
    public Optional<RecordCard> loadOwnedForUpdate(RecordCardId recordCardId, UserId userId) {
        return stored(recordCardId)
                .filter(card -> card.getUserId().equals(userId))
                .map(InMemoryRecordCardStore::copy);
    }

    @Override
    public void update(RecordCard recordCard) {
        long recordId = recordCard.getRunningRecordId().value();
        cardsByRecord.put(recordId, copy(recordCard));
        updateCount++;
    }

    // 음악 상태·key만 저장한 것으로 친다 — 페이크는 카드 전체를 사본으로 바꿔 둔다(글·사진 구성은 손대지 않은 값이다)
    @Override
    public void saveMusic(RecordCard recordCard) {
        cardsByRecord.put(recordCard.getRunningRecordId().value(), copy(recordCard));
        musicSaveCount++;
    }

    // 저장된(아직 고치기 전) 카드를 남긴다 — 실제 어댑터처럼 저장보다 먼저 불려야 직전 모습이다.
    // 페이크는 도메인 객체를 그대로 들고 있어, 수정 계산 전에 남긴 값과 비교하려면 storedSnapshots를 쓴다
    @Override
    public void snapshot(RecordCardId recordCardId, RecordCardSnapshotReason reason) {
        RecordCard stored = stored(recordCardId).orElseThrow();
        snapshots.add(new Snapshot(recordCardId.value(), reason, stored.getMemo().value(), stored.getImages().size()));
    }

    // 이력 ID는 남긴 순서(1부터) — 실제 IDENTITY처럼 나중에 남긴 이력이 크다
    @Override
    public Optional<Long> loadLastId(RecordCardId recordCardId) {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            if (snapshots.get(i).recordCardId().equals(recordCardId.value())) {
                return Optional.of((long) i + 1);
            }
        }
        return Optional.empty();
    }

    @Override
    public void delete(RecordCardId recordCardId) {
        cardsByRecord.values().removeIf(card -> card.getRecordCardId().equals(recordCardId));
    }

    private Optional<RecordCard> stored(RecordCardId recordCardId) {
        return cardsByRecord.values().stream()
                .filter(card -> card.getRecordCardId().equals(recordCardId))
                .findFirst();
    }

    // 저장 값 사본 — 꺼내 간 도메인 객체를 고쳐도 저장된 값이 바뀌지 않게 한다
    private static RecordCard copy(RecordCard card) {
        return RecordCard.restore(card.getRecordCardId().value())
                .runningRecordId(card.getRunningRecordId().value())
                .userId(card.getUserId().value())
                .visibility(card.getVisibility())
                .activityType(card.getActivityType())
                .baseMusicStatus(card.getBaseMusicStatus())
                .memo(card.getMemo().value())
                .displayOrder(card.getDisplayOrder().value())
                .thumbnailImageKey(card.getThumbnailImageKey().map(RecordCardImageKey::value).orElse(null))
                .baseMusicKey(card.getBaseMusicKey().map(MusicKey::value).orElse(null))
                .images(card.getImages().stream()
                        .map(image -> RecordCardImage.restore(image.getRecordCardImageId().value(),
                                image.getImageKey().value(),
                                image.getCaption().map(RecordCardImageCaption::value).orElse(null),
                                image.getMusicKey().map(MusicKey::value).orElse(null), image.getMusicStatus(),
                                image.getSortOrder()))
                        .toList())
                .build();
    }

    // 아래는 준비·검증 전용
    public int updateCount() {
        return updateCount;
    }

    public int musicSaveCount() {
        return musicSaveCount;
    }

    public List<Snapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    public void addSegments(long runningRecordId, List<String> segmentPolylines) {
        segmentPolylinesByRecord.put(runningRecordId, List.copyOf(segmentPolylines));
    }

    public void addRecord(long runningRecordId, UUID userId) {
        records.put(runningRecordId, new StoredRecord(userId, false));
    }

    public void addRecordInHiddenRoom(long runningRecordId, UUID userId) {
        records.put(runningRecordId, new StoredRecord(userId, true));
    }

    public void addCard(long runningRecordId, UUID recordCardId) {
        foreignCardIdsByRecord.put(runningRecordId, recordCardId);
    }

    public void hideRoomOf(long runningRecordId) {
        StoredRecord record = records.get(runningRecordId);
        records.put(runningRecordId, new StoredRecord(record.userId(), true));
    }

    // 다른 기록으로 동시에 만든 요청이 아직 커밋되지 않은 카드를 못 보고 옛 최댓값을 읽은 상황 — 다음 한 번만 "카드 없음"으로 읽는다
    public void readStaleMaxOnce() {
        this.staleMaxOnce = true;
    }

    public Optional<RecordCard> cardOf(long runningRecordId) {
        return Optional.ofNullable(cardsByRecord.get(runningRecordId));
    }

    public int cardCount() {
        return cardsByRecord.size();
    }
}
