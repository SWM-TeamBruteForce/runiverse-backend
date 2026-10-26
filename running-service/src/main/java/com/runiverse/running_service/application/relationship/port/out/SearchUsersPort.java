package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.List;

public interface SearchUsersPort {

    // 닉네임에 검색어가 들어간 사용자를 닉네임 순으로 읽는다. afterNickname이 null이면 처음부터.
    // 조회자 본인과 온보딩 전인 사용자는 담지 않는다
    List<UserSearchRow> search(UserId viewerId, String nicknameFilter, String afterNickname, int limit);
}
