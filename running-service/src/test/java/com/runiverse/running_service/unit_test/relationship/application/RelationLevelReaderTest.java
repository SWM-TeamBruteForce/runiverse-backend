package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.RelationLevelReader;
import com.runiverse.running_service.application.common.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.domain.common.vo.RelationLevel;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관계 등급 읽기 단위 테스트")
class RelationLevelReaderTest {

    private final UserId viewer = new UserId(UuidCreator.getTimeOrderedEpoch());
    private final UserId target = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private LoadRelationshipTypesPort loadRelationshipTypesPort;

    private RelationLevelReader reader;

    @BeforeEach
    void setUp() {
        reader = new RelationLevelReader(loadRelationshipTypesPort);
    }

    @Test
    @DisplayName("본인이면 관계 행을 읽지 않고 SELF다")
    void self() {
        // when
        RelationLevel level = reader.read(viewer, viewer);

        // then
        assertThat(level).isEqualTo(RelationLevel.SELF);
        verifyNoInteractions(loadRelationshipTypesPort);
    }

    @Test
    @DisplayName("관계 행이 없으면 OTHER다")
    void noRows() {
        // given
        givenTypes(null, null);

        // when & then
        assertThat(reader.read(viewer, target)).isEqualTo(RelationLevel.OTHER);
    }

    @Test
    @DisplayName("친구면 FRIEND다")
    void friend() {
        // given
        givenTypes(RelationshipType.FRIEND, RelationshipType.FRIEND);

        // when & then
        assertThat(reader.read(viewer, target)).isEqualTo(RelationLevel.FRIEND);
    }

    @ParameterizedTest
    @EnumSource(value = RelationshipType.class, names = {"NONE", "FRIEND_REQUEST_SENT", "FRIEND_REQUEST_RECEIVED"})
    @DisplayName("친구 요청을 주고받는 중이거나 끊긴 관계는 OTHER다")
    void notYetFriend(RelationshipType type) {
        // given
        givenTypes(type, null);

        // when & then
        assertThat(reader.read(viewer, target)).isEqualTo(RelationLevel.OTHER);
    }

    @Test
    @DisplayName("내가 상대를 막았으면 BLOCKED다")
    void viewerBlocked() {
        // given
        givenTypes(RelationshipType.BLOCKED, null);

        // when & then
        assertThat(reader.read(viewer, target)).isEqualTo(RelationLevel.BLOCKED);
    }

    @Test
    @DisplayName("상대가 나를 막았으면 내 행이 친구여도 BLOCKED다 — 상대 행까지 읽는다")
    void targetBlocked() {
        // given
        givenTypes(RelationshipType.FRIEND, RelationshipType.BLOCKED);

        // when & then
        assertThat(reader.read(viewer, target)).isEqualTo(RelationLevel.BLOCKED);
    }

    // 행 주인 관점의 관계 — null이면 그 방향의 행이 없다
    private void givenTypes(RelationshipType viewerToTarget, RelationshipType targetToViewer) {
        when(loadRelationshipTypesPort.loadTypes(viewer, List.of(target.value())))
                .thenReturn(viewerToTarget == null ? Map.of() : Map.of(target.value(), viewerToTarget));
        when(loadRelationshipTypesPort.loadTypes(target, List.of(viewer.value())))
                .thenReturn(targetToViewer == null ? Map.of() : Map.of(viewer.value(), targetToViewer));
    }
}
