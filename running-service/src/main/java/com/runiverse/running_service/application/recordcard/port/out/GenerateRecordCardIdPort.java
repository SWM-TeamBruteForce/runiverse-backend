package com.runiverse.running_service.application.recordcard.port.out;

import java.util.UUID;

public interface GenerateRecordCardIdPort {

    // 처음 만드는 카드의 ID — DB가 매기지 않는다. 작성 요청(9-1)이 정해 사진 key와 초안에 넣는다
    UUID generate();
}
