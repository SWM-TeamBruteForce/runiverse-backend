package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatusChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.DISCONNECTED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.FINISHED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.PAUSED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("러닝 화면 참가자 상태 변경 결과 단위 테스트")
public class LiveRunningStatusChangeTest {

    @Test
    @DisplayName("허용된 전이면 요청한 상태가 되고 바뀐 것으로 본다")
    void appliesAllowedTransition() {
        // when
        LiveRunningStatusChange change = LiveRunningStatusChange.of(RUNNING, PAUSED);

        // then
        assertThat(change.previous()).isEqualTo(RUNNING);
        assertThat(change.current()).isEqualTo(PAUSED);
        assertThat(change.changed()).isTrue();
    }

    @Test
    @DisplayName("상태가 없던 참가자의 START는 RUNNING으로 바뀐 것으로 본다")
    void appliesFirstStart() {
        // when -> 첫 진입이라 상대 화면에도 알려야 한다
        LiveRunningStatusChange change = LiveRunningStatusChange.of(null, RUNNING);

        // then
        assertThat(change.previous()).isNull();
        assertThat(change.current()).isEqualTo(RUNNING);
        assertThat(change.changed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = LiveRunningStatus.class, names = {"RUNNING", "PAUSED", "DISCONNECTED", "FINISHED"})
    @DisplayName("같은 상태 요청은 바뀌지 않은 것으로 본다")
    void sameStatusIsNotChange(LiveRunningStatus status) {
        // when -> 같은 PAUSE가 여러 번 와도 통지는 한 번만 나가야 한다
        LiveRunningStatusChange change = LiveRunningStatusChange.of(status, status);

        // then
        assertThat(change.current()).isEqualTo(status);
        assertThat(change.changed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = LiveRunningStatus.class, names = {"RUNNING", "PAUSED", "DISCONNECTED"})
    @DisplayName("FINISHED에서의 요청은 FINISHED 그대로다")
    void finishedStays(LiveRunningStatus requested) {
        // when -> 종료 후 닫히는 연결·늦은 좌표가 들어와도 결과는 그대로여야 한다
        LiveRunningStatusChange change = LiveRunningStatusChange.of(FINISHED, requested);

        // then
        assertThat(change.current()).isEqualTo(FINISHED);
        assertThat(change.changed()).isFalse();
    }

    @Test
    @DisplayName("상태가 없던 참가자의 끊김은 상태 없음 그대로다")
    void disconnectWithoutStatusStaysEmpty() {
        // when -> 시작 전에 끊긴 연결은 알릴 것이 없다
        LiveRunningStatusChange change = LiveRunningStatusChange.of(null, DISCONNECTED);

        // then
        assertThat(change.previous()).isNull();
        assertThat(change.current()).isNull();
        assertThat(change.changed()).isFalse();
    }

    @Test
    @DisplayName("끊겼던 참가자가 다시 오면 RUNNING으로 바뀐 것으로 본다")
    void recoversFromDisconnected() {
        // when -> 재연결이면 상대 화면의 끊김 표시를 풀어야 한다
        LiveRunningStatusChange change = LiveRunningStatusChange.of(DISCONNECTED, RUNNING);

        // then
        assertThat(change.current()).isEqualTo(RUNNING);
        assertThat(change.changed()).isTrue();
    }
}
