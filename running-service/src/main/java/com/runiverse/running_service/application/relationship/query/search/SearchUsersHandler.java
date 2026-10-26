package com.runiverse.running_service.application.relationship.query.search;

import com.runiverse.running_service.application.relationship.exception.InvalidUserSearchCursorException;
import com.runiverse.running_service.application.relationship.port.in.SearchUsersUsecase;
import com.runiverse.running_service.application.relationship.port.out.SearchUsersPort;
import com.runiverse.running_service.application.relationship.port.out.UserSearchRow;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchUsersHandler implements SearchUsersUsecase {

    private final SearchUsersPort searchUsersPort;
    private final GenerateViewUrlPort generateViewUrlPort;

    @Override
    public SearchUsersResult handle(SearchUsersQuery query) {
        UserId viewerId = new UserId(query.viewerId());
        String afterNickname = query.cursor() == null ? null : decodeCursor(query.cursor()).nickname();

        // 1. 한 장보다 하나 더 읽는다 — 넘치면 다음 페이지가 있다
        List<UserSearchRow> rows = searchUsersPort.search(
                viewerId, query.q().strip(), afterNickname, query.limit() + 1);

        // 2. 넘친 하나는 내리지 않고 다음 페이지가 있다는 표시로만 쓴다
        boolean hasNext = rows.size() > query.limit();
        List<UserSearchRow> page = hasNext ? rows.subList(0, query.limit()) : rows;
        String nextCursor = hasNext ? new UserSearchCursor(page.get(page.size() - 1).nickname()).encode() : null;

        return new SearchUsersResult(
                page.stream().map(this::toUser).toList(),
                nextCursor);
    }

    private UserSearchCursor decodeCursor(String value) {
        try {
            return UserSearchCursor.decode(value);
        } catch (InvalidUserSearchCursorException e) {
            log.info("[관계] 사용자 검색 실패: 커서 형식 오류");
            throw e;
        }
    }

    // 관계 행이 없으면 한 번도 관계를 맺지 않은 것이다 — 거절·취소로 되돌아간 NONE과 같게 보인다
    private SearchUsersResult.User toUser(UserSearchRow row) {
        return new SearchUsersResult.User(
                row.userId(),
                row.nickname(),
                row.profileImageKey() == null ? null : generateViewUrlPort.generate(row.profileImageKey()),
                row.viewerType() == null ? RelationshipType.NONE : row.viewerType());
    }
}
