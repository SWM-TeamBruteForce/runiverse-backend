package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.InvalidRecordCardIdException;

import java.util.UUID;

// 카드 ID — DB가 매기지 않고 작성 요청(9-1)이 정한다. 사진 key의 카드 폴더가 이 값이고, AI 서버도 이 값으로 저장한다
public record RecordCardId(UUID value) {

    public RecordCardId {
        if (value == null) {
            throw new InvalidRecordCardIdException();
        }
    }
}
