package com.runiverse.running_service.unit_test.relationship.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.relationship.exception.InvalidUserSearchCursorException;
import com.runiverse.running_service.application.relationship.port.out.SearchUsersPort;
import com.runiverse.running_service.application.relationship.port.out.UserSearchRow;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersHandler;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersQuery;
import com.runiverse.running_service.application.relationship.query.search.SearchUsersResult;
import com.runiverse.running_service.application.relationship.query.search.UserSearchCursor;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 검색 단위 테스트")
public class SearchUsersHandlerTest {

    @Mock
    private SearchUsersPort searchUsersPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private SearchUsersHandler handler;

    private UUID viewerId;

    @BeforeEach
    void setUp() {
        handler = new SearchUsersHandler(searchUsersPort, generateViewUrlPort);
        viewerId = UuidCreator.getTimeOrderedEpoch();
    }

    @Nested
    @DisplayName("페이지 테스트")
    class PageTest {

        @Test
        @DisplayName("한 장보다 하나 더 읽고 넘치지 않으면 마지막 페이지다")
        void notOverflowingIsLastPage() {
            // given -> 한 장 2개, 넘침 확인용으로 3개를 읽으려 한다
            UserSearchRow first = row("철수", null);
            UserSearchRow second = row("철수2", null);
            givenSearch("철수", null, 3, List.of(first, second));

            // when
            SearchUsersResult result = handler.handle(query("철수", null, 2));

            // then
            assertThat(result.items()).extracting(SearchUsersResult.User::userId)
                    .containsExactly(first.userId(), second.userId());
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("한 장보다 많으면 넘친 하나는 내리지 않고 마지막으로 내린 닉네임이 다음 커서가 된다")
        void overflowMakesNextCursorFromLastShownNickname() {
            // given
            UserSearchRow first = row("김철수", null);
            UserSearchRow second = row("철수", null);
            UserSearchRow overflow = row("철수왕", null);
            givenSearch("철수", null, 3, List.of(first, second, overflow));

            // when
            SearchUsersResult result = handler.handle(query("철수", null, 2));

            // then
            assertThat(result.items()).extracting(SearchUsersResult.User::nickname)
                    .containsExactly("김철수", "철수");
            assertThat(UserSearchCursor.decode(result.nextCursor())).isEqualTo(new UserSearchCursor("철수"));
        }

        @Test
        @DisplayName("커서가 있으면 그 닉네임 다음부터 읽는다")
        void cursorReadsAfterItsNickname() {
            // given
            String cursor = new UserSearchCursor("철수").encode();
            UserSearchRow next = row("철수왕", null);
            givenSearch("철수", "철수", 3, List.of(next));

            // when
            SearchUsersResult result = handler.handle(query("철수", cursor, 2));

            // then -> 스텁과 다른 위치가 넘어가면 엄격 스텁이 실패시킨다
            assertThat(result.items()).extracting(SearchUsersResult.User::nickname).containsExactly("철수왕");
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 목록이고 다음 커서가 없다")
        void emptyResult() {
            // given
            givenSearch("없는닉", null, 21, List.of());

            // when
            SearchUsersResult result = handler.handle(query("없는닉", null, 20));

            // then
            assertThat(result.items()).isEmpty();
            assertThat(result.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("항목 변환 테스트")
    class ItemTest {

        @Test
        @DisplayName("관계는 조회자 기준이고 관계 행이 없으면 NONE이다")
        void relationshipTypeIsViewerBasedAndNoneWhenAbsent() {
            // given -> 친구·요청 중·관계 없음이 한 결과에 섞인다
            givenSearch("철수", null, 21, List.of(
                    row("철수1", RelationshipType.FRIEND),
                    row("철수2", RelationshipType.FRIEND_REQUEST_SENT),
                    row("철수3", RelationshipType.FRIEND_REQUEST_RECEIVED),
                    row("철수4", RelationshipType.NONE),
                    row("철수5", null)));

            // when
            SearchUsersResult result = handler.handle(query("철수", null, 20));

            // then -> 버튼을 무엇으로 그릴지가 이 값에 달렸다
            assertThat(result.items()).extracting(SearchUsersResult.User::relationshipType)
                    .containsExactly(
                            RelationshipType.FRIEND,
                            RelationshipType.FRIEND_REQUEST_SENT,
                            RelationshipType.FRIEND_REQUEST_RECEIVED,
                            RelationshipType.NONE,
                            RelationshipType.NONE);
        }

        @Test
        @DisplayName("사진이 있으면 조회 URL을 만들고 없으면 null이다")
        void profileImageUrlOnlyWhenKeyExists() {
            // given
            UserSearchRow withImage = new UserSearchRow(UuidCreator.getTimeOrderedEpoch(), "철수", "profile/key.png", null);
            UserSearchRow withoutImage = row("철수2", null);
            givenSearch("철수", null, 21, List.of(withImage, withoutImage));
            given(generateViewUrlPort.generate("profile/key.png")).willReturn("https://cdn/profile/key.png");

            // when
            SearchUsersResult result = handler.handle(query("철수", null, 20));

            // then
            assertThat(result.items()).extracting(SearchUsersResult.User::profileImageUrl)
                    .containsExactly("https://cdn/profile/key.png", null);
        }

        @Test
        @DisplayName("검색어는 앞뒤 공백을 걷어 넘긴다")
        void nicknameFilterIsStripped() {
            // given
            givenSearch("철수", null, 21, List.of());

            // when & then -> 스텁과 다른 검색어가 넘어가면 엄격 스텁이 PotentialStubbingProblem으로 실패시킨다
            assertThat(handler.handle(query("  철수 ", null, 20)).items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("실패 테스트")
    class FailureTest {

        @Test
        @DisplayName("커서를 풀 수 없으면 예외가 발생하고 검색하지 않는다")
        void malformedCursorFails() {
            // when & then
            assertThatThrownBy(() -> handler.handle(query("철수", "!!!", 20)))
                    .isInstanceOf(InvalidUserSearchCursorException.class);
            verifyNoInteractions(searchUsersPort);
        }
    }

    private SearchUsersQuery query(String q, String cursor, int limit) {
        return new SearchUsersQuery(viewerId, q, cursor, limit);
    }

    private void givenSearch(String nicknameFilter, String afterNickname, int limit, List<UserSearchRow> rows) {
        given(searchUsersPort.search(new UserId(viewerId), nicknameFilter, afterNickname, limit)).willReturn(rows);
    }

    private static UserSearchRow row(String nickname, RelationshipType viewerType) {
        return new UserSearchRow(UuidCreator.getTimeOrderedEpoch(), nickname, null, viewerType);
    }
}
