package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.InvalidRecordCardImageIdException;

import java.util.UUID;

// 사진 ID — DB가 매기지 않고 사진 key를 발급할 때(9-1·9-3) 정한다. 사진 key의 사진 폴더가 이 값이고,
// AI 서버가 이 값으로 저장해 완료 결과가 어느 사진의 것인지 돌려준다
public record RecordCardImageId(UUID value) {

    public RecordCardImageId {
        if (value == null) {
            throw new InvalidRecordCardImageIdException();
        }
    }
}
