package com.runiverse.running_service.application.relationship.query.friends;

import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.relationship.exception.FriendListOwnerNotFoundException;
import com.runiverse.running_service.application.relationship.exception.InvalidFriendCursorException;
import com.runiverse.running_service.application.relationship.port.in.GetFriendsUsecase;
import com.runiverse.running_service.application.relationship.port.out.FriendKeyset;
import com.runiverse.running_service.application.relationship.port.out.FriendRow;
import com.runiverse.running_service.application.relationship.port.out.LoadFriendsPort;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetFriendsHandler implements GetFriendsUsecase {

    // 받은 요청 → 보낸 요청 → 친구 — 내가 답할 것이 먼저 보인다
    private static final List<RelationshipType> MY_GROUPS = List.of(
            RelationshipType.FRIEND_REQUEST_RECEIVED,
            RelationshipType.FRIEND_REQUEST_SENT,
            RelationshipType.FRIEND);
    // 누구에게 요청했고 누가 요청했는지는 본인만 안다
    private static final List<RelationshipType> OTHERS_GROUPS = List.of(RelationshipType.FRIEND);

    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadFriendsPort loadFriendsPort;
    private final GenerateViewUrlPort generateViewUrlPort;

    @Override
    public GetFriendsResult handle(GetFriendsQuery query) {
        UserId viewerId = new UserId(query.viewerId());
        UserId ownerId = new UserId(query.targetUserId());

        // 1. 없는 사용자와 탈퇴한 사용자를 구분하지 않는다
        if (loadUserByIdPort.loadById(ownerId).isEmpty()) {
            log.info("[관계] 친구 목록 조회 실패: 대상 없음 - targetUserId={}", ownerId.value());
            throw new FriendListOwnerNotFoundException();
        }

        // 2. 본인 목록만 요청 묶음을 담는다
        List<RelationshipType> groups = viewerId.equals(ownerId) ? MY_GROUPS : OTHERS_GROUPS;
        FriendCursor cursor = query.cursor() == null ? null : decodeCursor(query.cursor(), groups);

        // 3. 커서 묶음부터 차례로 읽어 한 장보다 하나 더 모은다 — 넘치면 다음 페이지가 있다.
        //    묶음마다 따로 읽어야 인덱스 순서를 그대로 쓴다
        String nicknameFilter = normalize(query.q());
        int start = cursor == null ? 0 : groups.indexOf(cursor.type());
        List<FriendRow> rows = new ArrayList<>();
        for (int i = start; i < groups.size() && rows.size() <= query.limit(); i++) {
            FriendKeyset after = cursor != null && i == start
                    ? new FriendKeyset(cursor.updatedAt(), cursor.userId())
                    : null;
            rows.addAll(loadFriendsPort.loadGroup(
                    ownerId, viewerId, groups.get(i), nicknameFilter, after, query.limit() + 1 - rows.size()));
        }

        // 4. 넘친 하나는 내리지 않고 다음 페이지가 있다는 표시로만 쓴다
        boolean hasNext = rows.size() > query.limit();
        List<FriendRow> page = hasNext ? rows.subList(0, query.limit()) : rows;
        String nextCursor = hasNext ? toCursor(page.get(page.size() - 1)).encode() : null;

        return new GetFriendsResult(
                page.stream().map(row -> toFriend(row, viewerId)).toList(),
                nextCursor);
    }

    // 다른 목록의 묶음을 가리키는 커서도 이 목록에서는 깨진 커서다 — 타인 목록에 요청 묶음 커서가 오는 경우
    private FriendCursor decodeCursor(String value, List<RelationshipType> groups) {
        FriendCursor cursor;
        try {
            cursor = FriendCursor.decode(value);
        } catch (InvalidFriendCursorException e) {
            log.info("[관계] 친구 목록 조회 실패: 커서 형식 오류");
            throw e;
        }
        if (!groups.contains(cursor.type())) {
            log.info("[관계] 친구 목록 조회 실패: 목록에 없는 묶음 - type={}", cursor.type());
            throw new InvalidFriendCursorException();
        }
        return cursor;
    }

    // 공백뿐인 검색어는 거르지 않는다
    private static String normalize(String q) {
        return q == null || q.isBlank() ? null : q.strip();
    }

    private static FriendCursor toCursor(FriendRow row) {
        return new FriendCursor(row.type(), row.updatedAt(), row.userId());
    }

    private GetFriendsResult.Friend toFriend(FriendRow row, UserId viewerId) {
        return new GetFriendsResult.Friend(
                row.userId(),
                row.nickname(),
                row.profileImageKey() == null ? null : generateViewUrlPort.generate(row.profileImageKey()),
                relationshipTypeOf(row, viewerId));
    }

    // 조회자 기준이다 — 본인 목록이면 행의 type과 같고, 행이 없으면 관계가 없는 것이다
    private static RelationshipType relationshipTypeOf(FriendRow row, UserId viewerId) {
        if (row.userId().equals(viewerId.value())) {
            return null;
        }
        return row.viewerType() == null ? RelationshipType.NONE : row.viewerType();
    }
}
