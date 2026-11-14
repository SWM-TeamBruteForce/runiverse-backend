package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.RecordCard;

public interface CreateRecordCardPort {

    // 새 카드와 사진을 넣는다 — 카드 ID는 작성 요청(9-1)이 정한 값 그대로다.
    // 제약에 걸리면 예외 대신 결과로 알린다 — 어느 제약인지에 따라 응답이 다르다(둘 다 409지만 코드가 다르다).
    // 걸린 뒤 트랜잭션은 더 쓸 수 없다 — 호출한 쪽은 결과를 보고 예외를 던져 롤백해야 한다
    CreateResult create(RecordCard recordCard);

    enum CreateResult {
        CREATED,
        // uk_record_card_running_record — 이 기록에 카드가 이미 있다. 기록 행 잠금 안이라 정상 경로에서는 오지 않는다(마지막 안전장치)
        RECORD_ALREADY_HAS_CARD,
        // uk_record_card_user_display_order — 같은 사람이 다른 기록으로 동시에 만들어 순위가 겹쳤다
        DISPLAY_ORDER_TAKEN
    }
}
