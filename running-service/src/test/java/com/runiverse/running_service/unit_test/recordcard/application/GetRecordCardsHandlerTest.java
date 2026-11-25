package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.RelationLevelReader;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.recordcard.exception.InvalidRecordCardCursorException;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.GenerateRecordCardViewUrlPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardSummariesPort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSummary;
import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsHandler;
import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsQuery;
import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsResult;
import com.runiverse.running_service.application.recordcard.query.list.RecordCardCursor;
import com.runiverse.running_service.domain.common.vo.RelationLevel;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 목록(10-3) 단위 테스트")
class GetRecordCardsHandlerTest {

    private static final UUID OWNER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID VIEWER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 3, 7, 10);

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;
    @Mock
    private RelationLevelReader relationLevelReader;
    @Mock
    private LoadRecordCardSummariesPort loadRecordCardSummariesPort;
    @Mock
    private GenerateRecordCardViewUrlPort generateRecordCardViewUrlPort;

    private GetRecordCardsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetRecordCardsHandler(loadPlayerProfilesPort, relationLevelReader, loadRecordCardSummariesPort,
                generateRecordCardViewUrlPort);
    }

    static Stream<Arguments> visibleByLevel() {
        return Stream.of(
                Arguments.of(RelationLevel.SELF, List.of(RecordCardVisibility.FRIENDS, RecordCardVisibility.PUBLIC,
                        RecordCardVisibility.PRIVATE)),
                Arguments.of(RelationLevel.FRIEND, List.of(RecordCardVisibility.FRIENDS, RecordCardVisibility.PUBLIC)),
                Arguments.of(RelationLevel.OTHER, List.of(RecordCardVisibility.PUBLIC)));
    }

    @ParameterizedTest
    @MethodSource("visibleByLevel")
    @DisplayName("관계 등급이 볼 수 있는 공개 범위만 읽는다 — 본인은 비공개까지, 친구는 친구 공개까지, 남은 공개만")
    void readsVisibleOnly(RelationLevel level, List<RecordCardVisibility> visibilities) {
        // given
        givenOwner();
        givenLevel(level);
        when(loadRecordCardSummariesPort.loadSummaries(new UserId(OWNER_ID), visibilities, null, 21))
                .thenReturn(List.of());

        // when
        GetRecordCardsResult result = handler.handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 20));

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("한 장보다 하나 더 오면 다음 커서를 주고, 다음 페이지는 그 순위보다 작은 것부터 읽는다")
    void paginates() {
        // given — 순위 5·4·3 중 두 장 요청
        givenOwner();
        givenLevel(RelationLevel.SELF);
        List<RecordCardVisibility> all = List.of(RecordCardVisibility.values());
        when(loadRecordCardSummariesPort.loadSummaries(new UserId(OWNER_ID), all, null, 3))
                .thenReturn(List.of(summary(5, null), summary(4, null), summary(3, null)));

        // when
        GetRecordCardsResult first = handler.handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 2));

        // then
        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isEqualTo(new RecordCardCursor(4).encode());

        // when — 다음 페이지
        when(loadRecordCardSummariesPort.loadSummaries(new UserId(OWNER_ID), all, 4, 3))
                .thenReturn(List.of(summary(3, null)));
        GetRecordCardsResult second = handler.handle(
                new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, first.nextCursor(), 2));

        // then
        assertThat(second.items()).hasSize(1);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("썸네일은 대표 사진의 작은 버전(thumbnail/) URL이고, 사진 없는 카드는 null이다")
    void thumbnailIsSmallVersion() {
        // given
        givenOwner();
        givenLevel(RelationLevel.OTHER);
        RecordCardSummary withPhoto = summary(2, "record-cards/u/c/i/IMG_1.jpg");
        RecordCardSummary withoutPhoto = summary(1, null);
        when(loadRecordCardSummariesPort.loadSummaries(any(), any(), any(), anyInt()))
                .thenReturn(List.of(withPhoto, withoutPhoto));
        when(generateRecordCardViewUrlPort.imageUrl("record-cards/u/c/i/thumbnail/IMG_1.jpg"))
                .thenReturn("https://thumb");

        // when
        List<GetRecordCardsResult.Item> items =
                handler.handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 20)).items();

        // then
        assertThat(items).containsExactly(
                new GetRecordCardsResult.Item(withPhoto.recordCardId(), "https://thumb", 3, STARTED_AT, 5020),
                new GetRecordCardsResult.Item(withoutPhoto.recordCardId(), null, 3, STARTED_AT, 5020));
    }

    @Test
    @DisplayName("대상이 없거나 탈퇴했으면 404이고 관계·카드를 읽지 않는다")
    void ownerNotFound() {
        // given
        when(loadPlayerProfilesPort.loadProfiles(List.of(OWNER_ID))).thenReturn(Map.of());

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 20)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(relationLevelReader, loadRecordCardSummariesPort);
    }

    @Test
    @DisplayName("차단 관계면 빈 목록이 아니라 404다")
    void blocked() {
        // given
        givenOwner();
        givenLevel(RelationLevel.BLOCKED);

        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, null, 20)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(loadRecordCardSummariesPort);
    }

    @Test
    @DisplayName("깨진 커서는 400이고 아무것도 읽지 않는다")
    void invalidCursor() {
        // when & then
        assertThatThrownBy(() -> handler.handle(new GetRecordCardsQuery(VIEWER_ID, OWNER_ID, "not-a-cursor!", 20)))
                .isInstanceOf(InvalidRecordCardCursorException.class);
        verifyNoInteractions(loadPlayerProfilesPort, relationLevelReader, loadRecordCardSummariesPort);
    }

    private void givenOwner() {
        when(loadPlayerProfilesPort.loadProfiles(List.of(OWNER_ID)))
                .thenReturn(Map.of(OWNER_ID, new PlayerProfile(OWNER_ID, "주인", null, null)));
    }

    private void givenLevel(RelationLevel level) {
        when(relationLevelReader.read(new UserId(VIEWER_ID), new UserId(OWNER_ID))).thenReturn(level);
    }

    private static RecordCardSummary summary(int displayOrder, String thumbnailImageKey) {
        return new RecordCardSummary(UuidCreator.getTimeOrderedEpoch(), thumbnailImageKey, 3, STARTED_AT, 5020,
                displayOrder);
    }
}
