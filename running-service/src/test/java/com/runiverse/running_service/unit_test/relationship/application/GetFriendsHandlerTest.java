package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.relationship.exception.FriendListOwnerNotFoundException;
import com.runiverse.running_service.application.relationship.exception.InvalidFriendCursorException;
import com.runiverse.running_service.application.relationship.port.out.FriendKeyset;
import com.runiverse.running_service.application.relationship.port.out.FriendRow;
import com.runiverse.running_service.application.relationship.port.out.LoadFriendsPort;
import com.runiverse.running_service.application.relationship.query.friends.FriendCursor;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsHandler;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsQuery;
import com.runiverse.running_service.application.relationship.query.friends.GetFriendsResult;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 목록 조회 단위 테스트")
public class GetFriendsHandlerTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 25, 12, 0, 0, 123_456_000);

    @Mock
    private LoadUserByIdPort loadUserByIdPort;

    @Mock
    private LoadFriendsPort loadFriendsPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private GetFriendsHandler handler;

    private UUID viewerId;
    private UUID otherId;

    @BeforeEach
    void setUp() {
        handler = new GetFriendsHandler(loadUserByIdPort, loadFriendsPort, generateViewUrlPort);
        viewerId = UuidCreator.getTimeOrderedEpoch();
        otherId = UuidCreator.getTimeOrderedEpoch();
    }

    @Nested
    @DisplayName("본인 목록 테스트")
    class MyListTest {

        @Test
        @DisplayName("받은 요청 → 보낸 요청 → 친구 순으로 묶음을 넘어가며 채운다")
        void fillsAcrossGroupsInOrder() {
            // given -> 한 장 2개, 넘침 확인용으로 하나 더 읽으려 한다
            givenOwnerExists(viewerId);
            FriendRow received = row(RelationshipType.FRIEND_REQUEST_RECEIVED, 0, null);
            FriendRow sent = row(RelationshipType.FRIEND_REQUEST_SENT, 0, null);
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_RECEIVED, null, 3, List.of(received));
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_SENT, null, 2, List.of(sent));
            givenGroup(viewerId, RelationshipType.FRIEND, null, 1, List.of());

            // when
            GetFriendsResult result = handler.handle(query(viewerId, null, null, 2));

            // then -> 마지막 묶음까지 읽어도 넘치지 않으면 마지막 페이지다
            assertThat(result.items()).extracting(GetFriendsResult.Friend::userId)
                    .containsExactly(received.userId(), sent.userId());
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("한 장보다 많으면 넘친 하나는 내리지 않고 마지막으로 내린 항목이 다음 커서가 된다")
        void overflowMakesNextCursorFromLastShownItem() {
            // given
            givenOwnerExists(viewerId);
            FriendRow first = row(RelationshipType.FRIEND_REQUEST_RECEIVED, 0, null);
            FriendRow second = row(RelationshipType.FRIEND_REQUEST_RECEIVED, -1, null);
            FriendRow overflow = row(RelationshipType.FRIEND_REQUEST_RECEIVED, -2, null);
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_RECEIVED, null, 3, List.of(first, second, overflow));

            // when
            GetFriendsResult result = handler.handle(query(viewerId, null, null, 2));

            // then -> 이미 다 찼으니 다음 묶음은 읽지 않는다
            assertThat(result.items()).extracting(GetFriendsResult.Friend::userId)
                    .containsExactly(first.userId(), second.userId());
            assertThat(FriendCursor.decode(result.nextCursor())).isEqualTo(new FriendCursor(
                    RelationshipType.FRIEND_REQUEST_RECEIVED, second.updatedAt(), second.userId()));
            verify(loadFriendsPort, never()).loadGroup(any(), any(), eq(RelationshipType.FRIEND_REQUEST_SENT),
                    any(), any(), anyInt());
        }

        @Test
        @DisplayName("묶음이 한 장을 딱 채우면 다음 묶음을 하나 더 읽어 다음 페이지가 있는지 본다")
        void exactFitPeeksNextGroup() {
            // given
            givenOwnerExists(viewerId);
            FriendRow first = row(RelationshipType.FRIEND_REQUEST_RECEIVED, 0, null);
            FriendRow second = row(RelationshipType.FRIEND_REQUEST_RECEIVED, -1, null);
            FriendRow peeked = row(RelationshipType.FRIEND_REQUEST_SENT, 0, null);
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_RECEIVED, null, 3, List.of(first, second));
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_SENT, null, 1, List.of(peeked));

            // when
            GetFriendsResult result = handler.handle(query(viewerId, null, null, 2));

            // then -> 커서는 받은 요청 묶음 끝을 가리켜 다음 요청이 보낸 요청 묶음으로 이어진다
            assertThat(result.items()).hasSize(2);
            assertThat(FriendCursor.decode(result.nextCursor()).type())
                    .isEqualTo(RelationshipType.FRIEND_REQUEST_RECEIVED);
            verify(loadFriendsPort, never()).loadGroup(any(), any(), eq(RelationshipType.FRIEND),
                    any(), any(), anyInt());
        }

        @Test
        @DisplayName("커서가 있으면 그 묶음부터 읽고 위치 조건은 첫 묶음에만 붙는다")
        void cursorStartsAtItsGroupWithKeysetOnlyThere() {
            // given
            givenOwnerExists(viewerId);
            UUID lastUserId = UuidCreator.getTimeOrderedEpoch();
            String cursor = new FriendCursor(RelationshipType.FRIEND_REQUEST_SENT, BASE, lastUserId).encode();
            FriendRow sent = row(RelationshipType.FRIEND_REQUEST_SENT, -1, null);
            FriendRow friend = row(RelationshipType.FRIEND, 0, null);
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_SENT, new FriendKeyset(BASE, lastUserId), 3,
                    List.of(sent));
            givenGroup(viewerId, RelationshipType.FRIEND, null, 2, List.of(friend));

            // when
            GetFriendsResult result = handler.handle(query(viewerId, null, cursor, 2));

            // then -> 이미 지나간 받은 요청 묶음은 다시 읽지 않는다
            assertThat(result.items()).extracting(GetFriendsResult.Friend::userId)
                    .containsExactly(sent.userId(), friend.userId());
            assertThat(result.nextCursor()).isNull();
            verify(loadFriendsPort, never()).loadGroup(any(), any(), eq(RelationshipType.FRIEND_REQUEST_RECEIVED),
                    any(), any(), anyInt());
        }

        @Test
        @DisplayName("본인 목록의 관계는 각 행의 상태 그대로다")
        void myListRelationshipTypeIsRowType() {
            // given -> 본인 목록은 조회자와 주인이 같아 조회자 관점이 곧 행의 type이다
            givenOwnerExists(viewerId);
            FriendRow received = row(RelationshipType.FRIEND_REQUEST_RECEIVED, 0, RelationshipType.FRIEND_REQUEST_RECEIVED);
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_RECEIVED, null, 21, List.of(received));
            givenGroup(viewerId, RelationshipType.FRIEND_REQUEST_SENT, null, 20, List.of());
            givenGroup(viewerId, RelationshipType.FRIEND, null, 20, List.of());

            // when
            GetFriendsResult result = handler.handle(query(viewerId, null, null, 20));

            // then
            assertThat(result.items()).extracting(GetFriendsResult.Friend::relationshipType)
                    .containsExactly(RelationshipType.FRIEND_REQUEST_RECEIVED);
        }
    }

    @Nested
    @DisplayName("타인 목록 테스트")
    class OthersListTest {

        @Test
        @DisplayName("타인 목록은 친구 묶음만 읽는다")
        void othersListReadsFriendGroupOnly() {
            // given
            givenOwnerExists(otherId);
            givenGroup(otherId, RelationshipType.FRIEND, null, 21, List.of());

            // when
            GetFriendsResult result = handler.handle(query(otherId, null, null, 20));

            // then -> 누구에게 요청했고 누가 요청했는지는 본인만 안다
            assertThat(result.items()).isEmpty();
            verify(loadFriendsPort, never()).loadGroup(any(), any(), eq(RelationshipType.FRIEND_REQUEST_RECEIVED),
                    any(), any(), anyInt());
            verify(loadFriendsPort, never()).loadGroup(any(), any(), eq(RelationshipType.FRIEND_REQUEST_SENT),
                    any(), any(), anyInt());
        }

        @Test
        @DisplayName("타인 목록의 관계는 조회자 기준이다 — 행이 없으면 NONE, 조회자 본인이면 null")
        void othersListRelationshipTypeIsViewerBased() {
            // given
            givenOwnerExists(otherId);
            FriendRow stranger = row(RelationshipType.FRIEND, 0, null);
            FriendRow mutualFriend = row(RelationshipType.FRIEND, -1, RelationshipType.FRIEND);
            FriendRow me = new FriendRow(viewerId, "나", null, RelationshipType.FRIEND, BASE.minusMinutes(2), null);
            givenGroup(otherId, RelationshipType.FRIEND, null, 21, List.of(stranger, mutualFriend, me));

            // when
            GetFriendsResult result = handler.handle(query(otherId, null, null, 20));

            // then -> 목록에서 바로 친구 요청 버튼을 그리는 값이다
            assertThat(result.items()).extracting(GetFriendsResult.Friend::relationshipType)
                    .containsExactly(RelationshipType.NONE, RelationshipType.FRIEND, null);
        }

        @Test
        @DisplayName("타인 목록에 요청 묶음을 가리키는 커서가 오면 예외가 발생하고 읽지 않는다")
        void requestGroupCursorOnOthersListFails() {
            // given -> 본인 목록에서 받은 커서를 타인 목록에 보낸 경우다
            givenOwnerExists(otherId);
            String cursor = new FriendCursor(RelationshipType.FRIEND_REQUEST_SENT, BASE,
                    UuidCreator.getTimeOrderedEpoch()).encode();

            // when & then
            assertThatThrownBy(() -> handler.handle(query(otherId, null, cursor, 20)))
                    .isInstanceOf(InvalidFriendCursorException.class);
            verifyNoInteractions(loadFriendsPort);
        }
    }

    @Nested
    @DisplayName("항목 변환 테스트")
    class ItemTest {

        @Test
        @DisplayName("사진이 있으면 조회 URL을 만들고 없으면 null이다")
        void profileImageUrlOnlyWhenKeyExists() {
            // given
            givenOwnerExists(otherId);
            FriendRow withImage = new FriendRow(UuidCreator.getTimeOrderedEpoch(), "철수", "profile/key.png",
                    RelationshipType.FRIEND, BASE, null);
            FriendRow withoutImage = row(RelationshipType.FRIEND, -1, null);
            givenGroup(otherId, RelationshipType.FRIEND, null, 21, List.of(withImage, withoutImage));
            given(generateViewUrlPort.generate("profile/key.png")).willReturn("https://cdn/profile/key.png");

            // when
            GetFriendsResult result = handler.handle(query(otherId, null, null, 20));

            // then
            assertThat(result.items()).extracting(GetFriendsResult.Friend::profileImageUrl)
                    .containsExactly("https://cdn/profile/key.png", null);
        }

        @Test
        @DisplayName("검색어는 앞뒤 공백을 걷어 넘긴다")
        void nicknameFilterIsStripped() {
            // given
            givenOwnerExists(otherId);
            givenGroup(otherId, RelationshipType.FRIEND, "철수", 21, null, List.of());

            // when & then -> 스텁과 다른 검색어가 넘어가면 엄격 스텁이 PotentialStubbingProblem으로 실패시킨다
            assertThat(handler.handle(query(otherId, "  철수 ", null, 20)).items()).isEmpty();
        }

        @Test
        @DisplayName("공백뿐인 검색어는 거르지 않는다")
        void blankNicknameFilterMeansNoFilter() {
            // given
            givenOwnerExists(otherId);
            givenGroup(otherId, RelationshipType.FRIEND, null, 21, List.of());

            // when & then
            assertThat(handler.handle(query(otherId, "   ", null, 20)).items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("실패 테스트")
    class FailureTest {

        @Test
        @DisplayName("목록 주인이 없으면 예외가 발생하고 목록을 읽지 않는다")
        void ownerNotFound() {
            // given -> 탈퇴한 사용자도 같다
            given(loadUserByIdPort.loadById(new UserId(otherId))).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> handler.handle(query(otherId, null, null, 20)))
                    .isInstanceOf(FriendListOwnerNotFoundException.class);
            verifyNoInteractions(loadFriendsPort);
        }

        @Test
        @DisplayName("커서를 풀 수 없으면 예외가 발생하고 목록을 읽지 않는다")
        void malformedCursorFails() {
            // given
            givenOwnerExists(viewerId);

            // when & then
            assertThatThrownBy(() -> handler.handle(query(viewerId, null, "!!!", 20)))
                    .isInstanceOf(InvalidFriendCursorException.class);
            verifyNoInteractions(loadFriendsPort);
        }

        @Test
        @DisplayName("관계 없음을 가리키는 커서는 어느 목록에서도 예외가 발생한다")
        void noneGroupCursorFails() {
            // given
            givenOwnerExists(viewerId);
            String cursor = new FriendCursor(RelationshipType.NONE, BASE, UuidCreator.getTimeOrderedEpoch()).encode();

            // when & then
            assertThatThrownBy(() -> handler.handle(query(viewerId, null, cursor, 20)))
                    .isInstanceOf(InvalidFriendCursorException.class);
            verifyNoInteractions(loadFriendsPort);
        }
    }

    private GetFriendsQuery query(UUID ownerId, String q, String cursor, int limit) {
        return new GetFriendsQuery(viewerId, ownerId, q, cursor, limit);
    }

    private void givenOwnerExists(UUID ownerId) {
        given(loadUserByIdPort.loadById(new UserId(ownerId))).willReturn(Optional.of(mock(User.class)));
    }

    private void givenGroup(UUID ownerId, RelationshipType type, FriendKeyset after, int limit, List<FriendRow> rows) {
        givenGroup(ownerId, type, null, limit, after, rows);
    }

    private void givenGroup(UUID ownerId, RelationshipType type, String nicknameFilter, int limit,
                            FriendKeyset after, List<FriendRow> rows) {
        given(loadFriendsPort.loadGroup(new UserId(ownerId), new UserId(viewerId), type, nicknameFilter, after, limit))
                .willReturn(rows);
    }

    // 묶음 안은 updated_at 역순이라 뒤로 갈수록 이른 시각이다
    private static FriendRow row(RelationshipType type, int minuteOffset, RelationshipType viewerType) {
        return new FriendRow(UuidCreator.getTimeOrderedEpoch(), "닉네임", null, type,
                BASE.plusMinutes(minuteOffset), viewerType);
    }
}
