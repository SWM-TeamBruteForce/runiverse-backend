package com.runiverse.running_service.application.recordcard.query.detail;

import com.runiverse.running_service.application.common.RelationLevelReader;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.recordcard.common.RouteArtConverter;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.in.GetRecordCardDetailUsecase;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardViewUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardDetailPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDetail;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.RelationLevel;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 8-1 기록 카드 상세 — 볼 수 없는 경우(없음·탈퇴·공개 범위 밖·차단)는 모두 404로 숨긴다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRecordCardDetailHandler implements GetRecordCardDetailUsecase {

    private final LoadRecordCardDetailPort loadRecordCardDetailPort;
    private final LoadPlayerProfilesPort loadPlayerProfilesPort;
    private final RelationLevelReader relationLevelReader;
    private final GenerateRecordCardViewUrlPort generateRecordCardViewUrlPort;
    private final GenerateViewUrlPort generateViewUrlPort;

    @Override
    public GetRecordCardDetailResult handle(GetRecordCardDetailQuery query) {
        // 1. 카드 — 없거나 지워졌으면 404
        RecordCardDetail card = loadRecordCardDetailPort.loadDetail(new RecordCardId(query.recordCardId()))
                .orElseThrow(RecordCardNotFoundException::new);

        // 2. 작성자 — users 행이 없으면 탈퇴다. 카드는 남아도 보이지 않는다. 같이 뛴 사람의 프로필도 한 번에 읽는다
        List<UUID> profileIds = new ArrayList<>(card.companionUserIds());
        profileIds.add(card.userId());
        Map<UUID, PlayerProfile> profiles = loadPlayerProfilesPort.loadProfiles(profileIds);
        PlayerProfile author = profiles.get(card.userId());
        if (author == null) {
            throw new RecordCardNotFoundException();
        }

        // 3. 공개 범위 — 보는 사람의 관계 등급이 모자라면 카드가 없는 것과 같다
        UserId viewerId = new UserId(query.viewerId());
        RelationLevel level = relationLevelReader.read(viewerId, new UserId(card.userId()));
        if (!card.visibility().isVisibleTo(level)) {
            throw new RecordCardNotFoundException();
        }

        // 4. 보이는 카드만 URL을 만든다 — 서명은 S3를 부르지 않지만 숨길 카드의 링크는 만들지 않는다
        return new GetRecordCardDetailResult(
                card.recordCardId(),
                toAuthor(author),
                level == RelationLevel.SELF,
                card.visibility(),
                card.activityType(),
                card.memo(),
                toRecord(card.record()),
                music(card.baseMusicStatus(), card.baseMusicKey()),
                card.images().stream().map(image -> toImage(image, card.thumbnailImageKey())).toList(),
                companions(card.companionUserIds(), profiles, viewerId),
                card.createdAt(),
                card.updatedAt());
    }

    // 같이 뛴 사람 — 탈퇴자는 프로필이 없어 빠지고, 보는 사람과 차단 관계인 사람(어느 쪽이 막았든)은 뺀다.
    // 방 인원이 몇 명뿐이라 사람마다 관계를 읽는다
    private List<GetRecordCardDetailResult.Author> companions(List<UUID> companionUserIds,
                                                              Map<UUID, PlayerProfile> profiles, UserId viewerId) {
        return companionUserIds.stream()
                .map(profiles::get)
                .filter(Objects::nonNull)
                .filter(profile -> relationLevelReader.read(viewerId, new UserId(profile.userId()))
                        != RelationLevel.BLOCKED)
                .map(this::toAuthor)
                .toList();
    }

    private GetRecordCardDetailResult.Author toAuthor(PlayerProfile profile) {
        return new GetRecordCardDetailResult.Author(profile.userId(), profile.nickname(),
                profile.profileImageKey() == null ? null : generateViewUrlPort.generate(profile.profileImageKey()));
    }

    private GetRecordCardDetailResult.Record toRecord(RecordCardDetail.Record record) {
        return new GetRecordCardDetailResult.Record(
                record.runningRecordId(),
                record.startedAt(),
                record.totalDistanceMeters(),
                record.totalDurationSeconds(),
                record.averagePaceSecondsPerKm(),
                RouteArtConverter.convert(record.segmentPolylines()));
    }

    // 썸네일은 카드가 사진 key를 복사해 둔 값이다 — 같은 key인 사진이 썸네일이다
    private GetRecordCardDetailResult.Image toImage(RecordCardDetail.Image image, String thumbnailImageKey) {
        return new GetRecordCardDetailResult.Image(
                image.recordCardImageId(),
                generateRecordCardViewUrlPort.imageUrl(image.imageKey()),
                image.caption(),
                image.imageKey().equals(thumbnailImageKey),
                music(image.musicStatus(), image.musicKey()));
    }

    // 다 만든 음악만 URL이 있다 — 만드는 중·실패면 key가 없다
    private GetRecordCardDetailResult.Music music(MusicStatus status, String musicKey) {
        String url = status == MusicStatus.COMPLETED && musicKey != null
                ? generateRecordCardViewUrlPort.musicUrl(musicKey)
                : null;
        return new GetRecordCardDetailResult.Music(status, url);
    }
}
