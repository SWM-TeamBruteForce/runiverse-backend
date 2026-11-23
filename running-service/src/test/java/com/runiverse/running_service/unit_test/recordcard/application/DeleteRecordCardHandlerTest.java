package com.runiverse.running_service.unit_test.recordcard.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.recordcard.command.delete.DeleteRecordCardCommand;
import com.runiverse.running_service.application.recordcard.command.delete.DeleteRecordCardHandler;
import com.runiverse.running_service.application.recordcard.exception.RecordCardNotFoundException;
import com.runiverse.running_service.application.recordcard.port.out.DeleteRecordCardPort;
import com.runiverse.running_service.application.recordcard.port.out.LoadRecordCardForUpdatePort;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.application.recordcard.port.out.SnapshotRecordCardPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 삭제 단위 테스트")
class DeleteRecordCardHandlerTest {

    private static final UUID CARD_ID = UuidCreator.getTimeOrderedEpoch();
    private static final UUID OWNER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private LoadRecordCardForUpdatePort loadRecordCardForUpdatePort;
    @Mock
    private SnapshotRecordCardPort snapshotRecordCardPort;
    @Mock
    private DeleteRecordCardPort deleteRecordCardPort;

    private DeleteRecordCardHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeleteRecordCardHandler(loadRecordCardForUpdatePort, snapshotRecordCardPort,
                deleteRecordCardPort);
    }

    @Test
    @DisplayName("잠그고 읽은 내 카드를 이력(DELETE)으로 남긴 뒤 지운다 — 순서가 바뀌면 직전 모습을 잃는다")
    void snapshotsThenDeletes() {
        // given
        RecordCard card = card(OWNER_ID);
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.of(card));

        // when
        handler.handle(new DeleteRecordCardCommand(OWNER_ID, CARD_ID));

        // then
        InOrder order = inOrder(loadRecordCardForUpdatePort, snapshotRecordCardPort, deleteRecordCardPort);
        order.verify(loadRecordCardForUpdatePort)
                .loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID));
        order.verify(snapshotRecordCardPort).snapshot(new RecordCardId(CARD_ID), RecordCardSnapshotReason.DELETE);
        order.verify(deleteRecordCardPort).delete(new RecordCardId(CARD_ID));
    }

    @Test
    @DisplayName("없는 카드(이미 지운 카드 포함)는 404이고 이력·삭제를 하지 않는다")
    void notFound() {
        // given
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(OWNER_ID)))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new DeleteRecordCardCommand(OWNER_ID, CARD_ID)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(snapshotRecordCardPort, deleteRecordCardPort);
    }

    @Test
    @DisplayName("남의 카드는 요청한 사람 ID로 찾아 없는 카드와 같은 404다")
    void othersCard() {
        // given
        UUID stranger = UuidCreator.getTimeOrderedEpoch();
        when(loadRecordCardForUpdatePort.loadOwnedForUpdate(new RecordCardId(CARD_ID), new UserId(stranger)))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(new DeleteRecordCardCommand(stranger, CARD_ID)))
                .isInstanceOf(RecordCardNotFoundException.class);
        verifyNoInteractions(snapshotRecordCardPort, deleteRecordCardPort);
    }

    private static RecordCard card(UUID ownerId) {
        return RecordCard.restore(CARD_ID)
                .runningRecordId(501L)
                .userId(ownerId)
                .visibility(RecordCardVisibility.PUBLIC)
                .activityType(RecordCardActivityType.DAILY)
                .baseMusicStatus(MusicStatus.GENERATING)
                .memo("한강 한 바퀴")
                .displayOrder(1)
                .images(List.of())
                .build();
    }
}
