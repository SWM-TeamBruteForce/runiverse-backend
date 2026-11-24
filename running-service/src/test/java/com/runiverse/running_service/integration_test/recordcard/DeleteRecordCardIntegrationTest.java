package com.runiverse.running_service.integration_test.recordcard;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.delete.DeleteRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.delete.DeleteRecordCardHandler;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.DisplayOrder;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.integration_test.fake.InMemoryRecordCardStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 9-4 삭제 — 스프링 없이 페이크로 조립한다. 실제 잠금·CASCADE·이력 테이블은 E2E가 본다
@DisplayName("기록 카드 삭제(9-4) 통합 테스트")
class DeleteRecordCardIntegrationTest {

    private static final long RECORD_ID = 501L;

    private UUID owner;
    private InMemoryRecordCardStore recordCardStore;
    private DeleteRecordCardHandler handler;

    @BeforeEach
    void setUp() {
        owner = UuidCreator.getTimeOrderedEpoch();
        recordCardStore = new InMemoryRecordCardStore();
        handler = new DeleteRecordCardHandler(recordCardStore, recordCardStore, recordCardStore, recordCardStore);
        recordCardStore.addRecord(RECORD_ID, owner);
    }

    @Test
    @DisplayName("지우면 직전 모습(사진 포함)이 이력으로 남고 카드가 사라져, 같은 기록으로 다시 만들 수 있다")
    void deleteThenRecreate() {
        // given
        RecordCard card = createCard();

        // when
        handler.handle(new DeleteRecordCardCommand(owner, card.getRecordCardId().value()));

        // then
        assertThat(recordCardStore.cardOf(RECORD_ID)).isEmpty();
        assertThat(recordCardStore.snapshots()).containsExactly(new InMemoryRecordCardStore.Snapshot(
                card.getRecordCardId().value(), RecordCardSnapshotReason.DELETE, "한강 한 바퀴", 1));
        assertThat(recordCardStore.loadDetail(card.getRecordCardId())).isEmpty();
        // 행이 없으니 새 카드 ID로 다시 만들 수 있다
        assertThat(recordCardStore.findIdByRunningRecord(new RunningRecordId(RECORD_ID))).isEmpty();
    }

    @Test
    @DisplayName("두 번 지우면 두 번째는 404이고 이력은 한 줄뿐이다")
    void deleteTwice() {
        // given
        RecordCard card = createCard();
        handler.handle(new DeleteRecordCardCommand(owner, card.getRecordCardId().value()));

        // when & then
        assertThatThrownBy(() -> handler.handle(new DeleteRecordCardCommand(owner, card.getRecordCardId().value())))
                .isInstanceOf(RecordCardNotFoundException.class);
        assertThat(recordCardStore.snapshots()).hasSize(1);
    }

    @Test
    @DisplayName("남의 카드는 404이고 카드도 이력도 그대로다")
    void othersCard() {
        // given
        RecordCard card = createCard();

        // when & then
        assertThatThrownBy(() -> handler.handle(
                new DeleteRecordCardCommand(UuidCreator.getTimeOrderedEpoch(), card.getRecordCardId().value())))
                .isInstanceOf(RecordCardNotFoundException.class);
        assertThat(recordCardStore.loadForUpdate(new RecordCardId(card.getRecordCardId().value()))).isPresent();
        assertThat(recordCardStore.snapshots()).isEmpty();
    }

    private RecordCard createCard() {
        RecordCard card = RecordCard.create(UuidCreator.getTimeOrderedEpoch(), RECORD_ID, owner,
                RecordCardVisibility.PUBLIC, RecordCardActivityType.DAILY, "한강 한 바퀴", DisplayOrder.FIRST,
                List.of(new RecordCard.ImageDraft(UuidCreator.getTimeOrderedEpoch(), "record-cards/u/c/a/a.jpg", null)),
                0);
        recordCardStore.create(card);
        return card;
    }
}
