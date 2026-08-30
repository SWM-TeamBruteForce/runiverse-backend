package com.runiverse.running_service.unit_test.running.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationHandler;
import com.runiverse.running_service.application.running.command.session.RegisterRunningSessionHandler;
import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionHandler;
import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningResult;
import com.runiverse.running_service.application.running.exception.NotRoomPlayerException;
import com.runiverse.running_service.application.running.exception.RunningRoomNotFoundException;
import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.in.StartRunningUsecase;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.PublishSupersedePort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.RunningSessionPort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.application.running.port.out.RunningRoomMembershipPort;
import com.runiverse.running_service.infrastructure.websocket.RunningSessionRegistryAdapter;
import com.runiverse.running_service.presentation.common.security.JwtHandshakeInterceptor;
import com.runiverse.running_service.presentation.common.websocket.WebSocketEnvelope;
import com.runiverse.running_service.presentation.running.websocket.RunningWebSocketHandler;
import com.runiverse.running_service.presentation.running.websocket.message.RunningMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
// 로그가 userId를 항상 읽지만 분기마다 호출 횟수가 달라 stubbing 검사는 끈다
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("러닝 WebSocket 핸들러 단위 테스트")
class RunningWebSocketHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 125L;
    // Location.MAX_SEQUENCE와 같은 값 — 구현이 바뀌면 이 상수도 같이 옮긴다
    private static final long MAX_SEQUENCE = 100_000L;
    // RUNNING_START가 세션에 새겨 두는 방의 목표 거리
    private static final int TARGET_DISTANCE_METERS = 5_000;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private WebSocketSession session;

    // 같은 유저의 두 번째 기기 — 중복 연결 테스트에서만 쓴다
    @Mock
    private WebSocketSession other;

    @Mock
    private StartRunningUsecase startRunningUsecase;

    @Mock
    private PublishSupersedePort publishSupersedePort;

    // 방 합류는 Redis 구독을 건드리므로 가짜로 둔다
    @Mock
    private RunningRoomMembershipPort runningRoomMembershipPort;

    // 좌표 적재도 Redis로 나가는 일이라 가짜로 둔다
    @Mock
    private AppendRunningTrackPort appendRunningTrackPort;

    // 누적 거리와 진행 발행도 Redis로 나간다
    @Mock
    private LoadRunningDistancePort loadRunningDistancePort;

    @Mock
    private SaveRunningDistancePort saveRunningDistancePort;

    @Mock
    private PublishRunningProgressPort publishRunningProgressPort;

    // 종료 확정은 DB·S3·Redis를 한꺼번에 건드리는 일이라 유스케이스째로 가짜다
    @Mock
    private FinishRunningUsecase finishRunningUsecase;

    private RunningWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        // 소켓 명부와 등록·해제 유스케이스는 상태만 들고 있는 POJO라 실제 구현을 쓴다
        // — 중복 연결 판정이 진짜로 도는지 봐야 한다. 인스턴스 밖으로 나가는 것만 가짜다
        RunningSessionPort sessionPort = new RunningSessionRegistryAdapter();
        handler = new RunningWebSocketHandler(
                jsonMapper,
                startRunningUsecase,
                new RegisterRunningSessionHandler(sessionPort, runningRoomMembershipPort, publishSupersedePort),
                new RemoveRunningSessionHandler(sessionPort, runningRoomMembershipPort),
                new UpdateRunningLocationHandler(appendRunningTrackPort, loadRunningDistancePort,
                        saveRunningDistancePort, publishRunningProgressPort),
                finishRunningUsecase);
        // 좌표를 한 번도 못 받은 상태에서 시작한다 — 누적 거리는 이 테스트의 관심사가 아니다
        given(loadRunningDistancePort.loadDistance(anyLong(), any()))
                .willReturn(RunningDistance.empty());
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(authenticated());
        given(other.getId()).willReturn("session-2");
        given(other.getAttributes()).willReturn(authenticated());
    }

    // 핸드셰이크 인터셉터가 채워 넣는 값.
    // 핸들러가 RUNNING_START에서 runningRoomId를 더 넣으므로 실제 세션처럼 가변이어야 한다
    private static Map<String, Object> authenticated() {
        // 실제 세션(StandardWebSocketSession)은 ConcurrentHashMap이라 null 값을 거부한다.
        // HashMap으로 흉내 내면 목표 거리가 없는 솔로 방에서 나는 NPE를 놓친다
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        attributes.put(JwtHandshakeInterceptor.USER_ID, new UserId(USER_ID));
        return attributes;
    }

    private static TextMessage runningStart(String data) {
        return new TextMessage("""
                {"event":"RUNNING_START","data":%s}""".formatted(data));
    }

    private static TextMessage locationUpdate(String data) {
        return new TextMessage("""
                {"event":"RUNNING_LOCATION_UPDATE","data":%s}""".formatted(data));
    }

    private static TextMessage runningFinish(String data) {
        return new TextMessage("""
                {"event":"RUNNING_FINISH","data":%s}""".formatted(data));
    }

    // 단말이 못 잰 값을 뺀 좌표 — Location.isValid()가 요구하는 것만 담았다.
    // 케이던스는 보수 센서가, 속도·방위는 GPS 픽스가 있어야 온다
    private static String pointWithoutOptionalFields(int sequence) {
        return """
                {"sequence":%d,"latitude":35.1795543,"longitude":129.0756416,\
                "accuracyMeters":6.2,"recordedAt":"2026-07-25T19:10:30"}""".formatted(sequence);
    }

    // api-spec 5-D의 좌표 한 개 — 단말이 모두 측정한 정상 배치.
    // Long.MAX_VALUE까지 실어 보낼 수 있어야 커서를 끝으로 미는 좌표를 재현한다
    private static String point(long sequence) {
        return """
                {"sequence":%d,"latitude":35.1795543,"longitude":129.0756416,\
                "altitudeMeters":18.4,"accuracyMeters":6.2,"speedMetersPerSecond":2.8,\
                "headingDegrees":85.3,"cadenceSpm":165,"currentPaceSecondsPerKm":345,\
                "recordedAt":"2026-07-25T19:10:30"}""".formatted(sequence);
    }

    @Test
    @DisplayName("HEALTH_CHECK를 보내면 HEALTH_CHECKED로 응답한다")
    void respondsHealthChecked() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"HEALTH_CHECK","data":{}}"""));

        // then
        WebSocketEnvelope sent = captureSent();
        assertThat(sent.event()).isEqualTo("HEALTH_CHECKED");
        assertThat((Map<?, ?>) sent.data()).isEmpty();
    }

    @Test
    @DisplayName("JSON이 깨져 봉투를 읽지 못하면 MALFORMED_MESSAGE로 응답한다")
    void respondsMalformedMessage() throws Exception {
        // when
        handler.handleMessage(session, text("this is not json"));

        // then
        assertThatError(captureSent(), "MALFORMED_MESSAGE", null);
    }

    @Test
    @DisplayName("event가 없으면 MISSING_MESSAGE_TYPE으로 응답한다")
    void respondsMissingMessageType() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"data":{}}"""));

        // then
        assertThatError(captureSent(), "MISSING_MESSAGE_TYPE", null);
    }

    @Test
    @DisplayName("event가 공백뿐이면 MISSING_MESSAGE_TYPE으로 응답한다")
    void respondsMissingMessageTypeOnBlank() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"   ","data":{}}"""));

        // then
        assertThatError(captureSent(), "MISSING_MESSAGE_TYPE", null);
    }

    @Test
    @DisplayName("모르는 event면 UNSUPPORTED_MESSAGE_TYPE과 함께 받은 event를 sourceType으로 돌려준다")
    void respondsUnsupportedMessageType() throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"MATCH_REQUEST","data":{}}"""));

        // then
        assertThatError(captureSent(), "UNSUPPORTED_MESSAGE_TYPE", "MATCH_REQUEST");
    }

    @Test
    @DisplayName("S→C 전용 타입을 클라가 보내면 UNSUPPORTED_MESSAGE_TYPE으로 응답한다")
    void rejectsServerToClientType() throws Exception {
        // given -> HEALTH_CHECKED는 서버만 보내는 타입이다
        // when
        handler.handleMessage(session, text("""
                {"event":"HEALTH_CHECKED","data":{}}"""));

        // then
        assertThatError(captureSent(), "UNSUPPORTED_MESSAGE_TYPE", "HEALTH_CHECKED");
    }

    @Test
    @DisplayName("RUNNING_START를 보내면 유스케이스를 태우고 RUNNING_STARTED로 응답한다")
    void respondsRunningStarted() throws Exception {
        // given
        given(startRunningUsecase.handle(new StartRunningCommand(USER_ID, ROOM_ID)))
                .willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then
        WebSocketEnvelope sent = captureSent();
        assertThat(sent.event()).isEqualTo("RUNNING_STARTED");
    }

    @Test
    @DisplayName("runningRoomId가 없으면 유스케이스를 태우지 않고 INVALID_REQUEST로 응답한다")
    void respondsInvalidRequestWithoutRoomId() throws Exception {
        // when -> WS에는 @Valid 파이프라인이 없어 핸들러가 직접 걸러야 한다
        handler.handleMessage(session, runningStart("{}"));

        // then
        assertThatError(captureSent(), "INVALID_REQUEST", "RUNNING_START");
        verifyNoInteractions(startRunningUsecase);
    }

    @Test
    @DisplayName("유스케이스가 튕겨내면 그 에러 코드를 ERROR로 돌려준다")
    void respondsUsecaseErrorCode() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willThrow(new RunningRoomNotFoundException());

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then -> 코드·문구의 정본은 application ErrorCode다
        assertThatError(captureSent(), "ROOM_NOT_FOUND", "RUNNING_START");
    }

    @Test
    @DisplayName("같은 유저가 다른 기기로 들어오면 이전 연결을 4001로 닫는다")
    void closesSupersededSession() throws Exception {
        // given -> 첫 기기가 이미 붙어 있다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when -> 기기를 바꿔 다시 들어온다
        handler.handleMessage(other, runningStart("""
                {"runningRoomId":125}"""));

        // then -> 마지막 것이 이긴다. 두 소켓이 살아 있으면 좌표 트랙이 섞인다
        ArgumentCaptor<CloseStatus> captor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(4001);
        assertThat(captureSent(other).event()).isEqualTo("RUNNING_STARTED");
    }

    @Test
    @DisplayName("등록에 성공하면 다른 인스턴스가 옛 연결을 닫도록 통지한다")
    void publishesSupersedeNotification() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then -> 밀어낼 옛 연결이 이 인스턴스에 없어도 다른 인스턴스에는 남아 있을 수 있다
        verify(publishSupersedePort).publish(USER_ID, ROOM_ID, "session-1");
    }

    @Test
    @DisplayName("유스케이스가 실패하면 기존 연결을 끊지 않는다")
    void keepsPreviousSessionWhenUsecaseFails() throws Exception {
        // given -> 첫 기기는 성공, 두 번째 요청은 유스케이스가 튕겨낸다
        given(startRunningUsecase.handle(any()))
                .willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS))
                .willThrow(new RunningRoomNotFoundException());
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(other, runningStart("""
                {"runningRoomId":999}"""));

        // then -> 잘못된 요청 하나가 멀쩡히 뛰는 기기를 끊어서는 안 된다
        verify(session, never()).close(any(CloseStatus.class));
        assertThatError(captureSent(other), "ROOM_NOT_FOUND", "RUNNING_START");
    }

    @Test
    @DisplayName("같은 소켓이 RUNNING_START를 두 번 보내도 자기 자신을 끊지 않는다")
    void doesNotCloseItselfOnResend() throws Exception {
        // given -> 재연결 뒤 클라가 같은 메시지를 다시 보내는 정상 경로
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("목표 거리가 없는 솔로 방도 RUNNING_STARTED로 응답한다")
    void respondsRunningStartedForRoomWithoutTarget() throws Exception {
        // given -> 솔로 방은 target_distance가 nullable이다(erd.md).
        // 세션 attribute는 ConcurrentHashMap이라 null을 그대로 넣으면 NPE가 나고,
        // 그 예외가 핸들러 밖으로 새면 Spring이 세션을 1011로 닫는다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, null));

        // when
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // then
        assertThat(captureLastSent(session).event()).isEqualTo("RUNNING_STARTED");
    }

    @Test
    @DisplayName("목표 거리가 없으면 좌표 배치에도 null로 실어 보낸다")
    void passesNullTargetToLocationCommand() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, null));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(0))));

        // then -> 목표가 없다는 사실이 진행 통지까지 그대로 흘러야 한다
        verify(appendRunningTrackPort).append(eq(ROOM_ID), any(), anyList());
    }

    @Test
    @DisplayName("RUNNING_START 없이 좌표를 보내면 RUNNING_NOT_STARTED로 응답하고 적재하지 않는다")
    void rejectsLocationBeforeStart() throws Exception {
        // when -> 방은 RUNNING_START가 정한다(api-spec 5-D). 세션에 방이 없으면 쓸 곳을 모른다
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(0))));

        // then -> 형식이 아니라 순서 문제라 INVALID_REQUEST와 가른다
        assertThatError(captureSent(), "RUNNING_NOT_STARTED", "RUNNING_LOCATION_UPDATE");
        verifyNoInteractions(appendRunningTrackPort);
    }

    @Test
    @DisplayName("RUNNING_START를 마친 뒤 좌표를 보내면 세션이 기억한 방으로 적재한다")
    void appendsLocationToStartedRoom() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(0))));

        // then
        verify(appendRunningTrackPort).append(eq(ROOM_ID), eq(new UserId(USER_ID)), anyList());
    }

    @Test
    @DisplayName("클라가 runningRoomId를 실어 보내도 무시하고 세션이 기억한 방에 적재한다")
    void ignoresClientSuppliedRoomId() throws Exception {
        // given -> 참가하지 않은 방을 클라가 지정할 수 있으면 남의 방에 트랙이 쌓인다.
        // 필드를 빼도 구버전 앱이 계속 보낼 수 있으므로 무시되는지까지 못 박는다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"runningRoomId":999999,"locations":[%s]}""".formatted(point(0))));

        // then
        verify(appendRunningTrackPort).append(eq(ROOM_ID), eq(new UserId(USER_ID)), anyList());
    }

    @Test
    @DisplayName("보낸 좌표는 순번 그대로 트랙 포인트로 옮겨진다")
    void mapsLocationsToTrackPoints() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when -> 클라는 1~2초 간격으로 모아 10초마다 배치로 보낸다
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s,%s]}""".formatted(point(0), point(1))));

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(appendRunningTrackPort).append(eq(ROOM_ID), any(), captor.capture());
        assertThat(captor.getValue()).extracting(TrackPoint::sequence).containsExactly(0L, 1L);
    }

    @Test
    @DisplayName("단말이 못 잰 선택 필드가 비어 있어도 좌표를 적재한다")
    void appendsLocationWithMissingOptionalFields() throws Exception {
        // given -> Location.isValid()는 거리 계산에 필요한 값만 막는다.
        // 속도·방위·케이던스·페이스가 없는 배치도 서버가 받아들여야 한다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(pointWithoutOptionalFields(0))));

        // then -> 배치 하나가 통째로 사라지면 그 10초가 빈다(api-spec 5-D)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(appendRunningTrackPort).append(eq(ROOM_ID), eq(new UserId(USER_ID)), captor.capture());
        // 못 잰 값은 0이 아니라 null로 내려가야 한다 — TrackPoint가 primitive로 돌아가면
        // 여기서 언박싱 NPE가 나고, 기본값으로 메우면 안 뛴 케이던스가 0으로 기록된다
        assertThat(captor.getValue()).singleElement().satisfies(point -> {
            assertThat(point.altitudeMeters()).isNull();
            assertThat(point.speedMetersPerSecond()).isNull();
            assertThat(point.headingDegrees()).isNull();
            assertThat(point.cadenceSpm()).isNull();
            assertThat(point.currentPaceSecondsPerKm()).isNull();
            // 필수 그룹은 그대로 실려 있어야 선택 필드가 빠졌다고 배치가 버려진 게 아님이 된다
            assertThat(point.sequence()).isZero();
            assertThat(point.accuracyMeters()).isEqualTo(6.2);
        });
    }

    @Test
    @DisplayName("순번이 상한을 넘으면 적재하지 않고 INVALID_REQUEST로 응답한다")
    void rejectsSequenceBeyondUpperBound() throws Exception {
        // given -> Redis 커서(last)는 되돌아오지 않는다. 튄 순번이 한 번 실리면
        // 남은 러닝의 정상 좌표가 전부 sequence > last에 걸려 버려진다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(MAX_SEQUENCE + 1))));

        // then
        verify(appendRunningTrackPort, never()).append(anyLong(), any(), anyList());
        assertThatError(captureLastSent(session), "INVALID_REQUEST", "RUNNING_LOCATION_UPDATE");
    }

    @Test
    @DisplayName("Long.MAX_VALUE 순번은 커서에 닿기 전에 막는다")
    void rejectsLongMaxValueSequence() throws Exception {
        // given -> 클라 버그 하나로 TTL 6시간 동안 트랙이 통째로 비는 경로다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(Long.MAX_VALUE))));

        // then
        verify(appendRunningTrackPort, never()).append(anyLong(), any(), anyList());
        assertThatError(captureLastSent(session), "INVALID_REQUEST", "RUNNING_LOCATION_UPDATE");
    }

    @Test
    @DisplayName("순번 상한 값 자체는 정상 좌표로 받는다")
    void acceptsSequenceAtUpperBound() throws Exception {
        // given -> 상한은 sanity bound다. 경계를 한 칸 잘못 잡으면 멀쩡한 배치를 버린다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(MAX_SEQUENCE))));

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(appendRunningTrackPort).append(eq(ROOM_ID), any(), captor.capture());
        assertThat(captor.getValue()).extracting(TrackPoint::sequence).containsExactly(MAX_SEQUENCE);
    }

    @Test
    @DisplayName("순번이 튄 배치를 튕겨도 이어지는 정상 배치는 그대로 적재한다")
    void keepsAppendingAfterRejectedBatch() throws Exception {
        // given -> 이번 버그의 핵심. 튄 좌표가 그 배치 10초만 잃게 하고
        // 남은 러닝까지 끌고 가지 않아야 한다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(Long.MAX_VALUE))));

        // when -> 클라는 ERROR를 받아도 러닝을 계속하며 다음 배치를 보낸다(api-spec 5-D)
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s,%s]}""".formatted(point(12), point(13))));

        // then -> 튕긴 배치만 빠지고 뒤 배치는 살아 있어야 한다
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(appendRunningTrackPort).append(eq(ROOM_ID), any(), captor.capture());
        assertThat(captor.getValue()).extracting(TrackPoint::sequence).containsExactly(12L, 13L);
    }

    @Test
    @DisplayName("좌표 저장에 실패하면 코드로 알리되 러닝을 끊지 않는다")
    void respondsTrackUnavailableWithoutClosing() throws Exception {
        // given -> 원본은 클라 로컬 트랙에 남아 있어 재연결로 복구된다(api-spec 5-D).
        // 저장소 장애 하나로 달리는 사람을 끊어서는 안 된다
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));
        given(appendRunningTrackPort.append(anyLong(), any(), anyList()))
                .willThrow(new RunningTrackUnavailableException());

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(0))));

        // then
        assertThatError(
                captureLastSent(session), "RUNNING_TRACK_UNAVAILABLE", "RUNNING_LOCATION_UPDATE");
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("locations가 비어 있으면 INVALID_REQUEST로 응답하고 적재하지 않는다")
    void rejectsEmptyLocations() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[]}"""));

        // then -> 방이 정해져 있어도 형식이 틀린 건 INVALID_REQUEST다
        assertThatError(captureLastSent(session), "INVALID_REQUEST", "RUNNING_LOCATION_UPDATE");
        verifyNoInteractions(appendRunningTrackPort);
    }

    @Test
    @DisplayName("RUNNING_START가 실패하면 방을 기억하지 않아 이어진 좌표도 거부한다")
    void doesNotRememberRoomWhenStartFails() throws Exception {
        // given
        given(startRunningUsecase.handle(any())).willThrow(new RunningRoomNotFoundException());
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));

        // when
        handler.handleMessage(session, locationUpdate("""
                {"locations":[%s]}""".formatted(point(0))));

        // then -> 실패한 요청이 방을 남기면 참가자가 아닌 방에 좌표가 쌓인다
        assertThatError(captureLastSent(session), "RUNNING_NOT_STARTED", "RUNNING_LOCATION_UPDATE");
        verifyNoInteractions(appendRunningTrackPort);
    }

    @Test
    @DisplayName("RUNNING_FINISH를 보내면 유스케이스를 태우고 RUNNING_FINISHED로 응답한다")
    void respondsRunningFinished() throws Exception {
        // given
        started();

        // when
        handler.handleMessage(session, runningFinish("""
                {"forced":false}"""));

        // then -> 클라는 이 ack를 받고 로컬 트랙을 지운 뒤 REST로 결과를 본다(api-spec 5-D)
        assertThat(captureLastSent(session).event()).isEqualTo("RUNNING_FINISHED");
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, USER_ID, false));
    }

    @Test
    @DisplayName("조기 종료 의사는 그대로 유스케이스에 전달된다")
    void passesForcedFlag() throws Exception {
        // given
        started();

        // when -> forced는 의사일 뿐 최종 상태는 서버가 확정한 거리로 정해진다
        handler.handleMessage(session, runningFinish("""
                {"forced":true}"""));

        // then
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, USER_ID, true));
    }

    @Test
    @DisplayName("RUNNING_START 없이 종료를 보내면 RUNNING_NOT_STARTED로 응답한다")
    void rejectsFinishBeforeStart() throws Exception {
        // when -> 세션에 방이 없으면 무엇을 끝낼지 모른다
        handler.handleMessage(session, runningFinish("""
                {"forced":false}"""));

        // then
        assertThatError(captureSent(), "RUNNING_NOT_STARTED", "RUNNING_FINISH");
        verifyNoInteractions(finishRunningUsecase);
    }

    @Test
    @DisplayName("forced가 없으면 유스케이스를 태우지 않고 INVALID_REQUEST로 응답한다")
    void respondsInvalidRequestWithoutForced() throws Exception {
        // given
        started();

        // when -> WS에는 @Valid 파이프라인이 없어 핸들러가 직접 걸러야 한다
        handler.handleMessage(session, runningFinish("{}"));

        // then
        assertThatError(captureLastSent(session), "INVALID_REQUEST", "RUNNING_FINISH");
        verifyNoInteractions(finishRunningUsecase);
    }

    @Test
    @DisplayName("종료 유스케이스가 튕겨내면 그 에러 코드를 ERROR로 돌려준다")
    void respondsFinishUsecaseErrorCode() throws Exception {
        // given
        started();
        willThrow(new NotRoomPlayerException()).given(finishRunningUsecase).handle(any());

        // when
        handler.handleMessage(session, runningFinish("""
                {"forced":false}"""));

        // then -> ack 대신 코드가 나간다. 클라는 로컬 트랙을 지우지 않는다
        assertThatError(captureLastSent(session), "NOT_ROOM_PLAYER", "RUNNING_FINISH");
    }

    @Test
    @DisplayName("종료에 실린 runningRoomId도 무시하고 세션이 기억한 방을 끝낸다")
    void ignoresClientSuppliedRoomIdOnFinish() throws Exception {
        // given -> 남의 방을 지정해 끝내지 못하게 한다. 구버전 앱이 계속 실어 보낼 수 있다
        started();

        // when
        handler.handleMessage(session, runningFinish("""
                {"runningRoomId":999999,"forced":false}"""));

        // then
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, USER_ID, false));
    }

    @Test
    @DisplayName("ack를 놓친 클라가 다시 보내도 RUNNING_FINISHED를 또 준다")
    void acksRepeatedFinish() throws Exception {
        // given -> 세션의 방을 지우면 재전송이 RUNNING_NOT_STARTED로 걸려
        // 클라가 로컬 트랙을 영영 못 지운다. 멱등은 유스케이스가 책임진다
        started();
        handler.handleMessage(session, runningFinish("""
                {"forced":false}"""));

        // when
        handler.handleMessage(session, runningFinish("""
                {"forced":false}"""));

        // then
        assertThat(captureLastSent(session).event()).isEqualTo("RUNNING_FINISHED");
        verify(finishRunningUsecase, times(2))
                .handle(new FinishRunningCommand(ROOM_ID, USER_ID, false));
    }

    // 새 메시지 타입이 생겨도 연결이 끊기지 않는지 전수로 확인한다
    @ParameterizedTest(name = "{0}")
    @EnumSource(RunningMessageType.class)
    @DisplayName("어떤 타입을 받아도 응답을 보내고 연결을 끊지 않는다")
    void neverClosesConnection(RunningMessageType type) throws Exception {
        // when
        handler.handleMessage(session, text("""
                {"event":"%s","data":{}}""".formatted(type.name())));

        // then
        assertThat(captureSent()).isNotNull();
        verify(session, never()).close();
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("잘못된 메시지를 받아도 연결을 끊지 않는다")
    void neverClosesConnectionOnMalformedMessage() throws Exception {
        // when
        handler.handleMessage(session, text("{"));

        // then
        verify(session, never()).close();
        verify(session, never()).close(any(CloseStatus.class));
    }

    // 종료 케이스는 전부 "이미 시작한 러닝"에서 출발한다
    private void started() throws Exception {
        given(startRunningUsecase.handle(any())).willReturn(new StartRunningResult(ROOM_ID, TARGET_DISTANCE_METERS));
        handler.handleMessage(session, runningStart("""
                {"runningRoomId":125}"""));
    }

    private TextMessage text(String payload) {
        return new TextMessage(payload);
    }

    private WebSocketEnvelope captureSent() throws Exception {
        return captureSent(session);
    }

    private WebSocketEnvelope captureSent(WebSocketSession target) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(target).sendMessage(captor.capture());
        return jsonMapper.readValue(captor.getValue().getPayload(), WebSocketEnvelope.class);
    }

    // RUNNING_START ack가 먼저 나간 뒤의 응답을 보려면 마지막 것을 집어야 한다
    private WebSocketEnvelope captureLastSent(WebSocketSession target) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(target, atLeastOnce()).sendMessage(captor.capture());
        List<TextMessage> sent = captor.getAllValues();
        return jsonMapper.readValue(sent.get(sent.size() - 1).getPayload(), WebSocketEnvelope.class);
    }

    private void assertThatError(WebSocketEnvelope sent, String code, String sourceType) {
        assertThat(sent.event()).isEqualTo("ERROR");
        Map<?, ?> data = (Map<?, ?>) sent.data();
        assertThat(data.get("code")).isEqualTo(code);
        assertThat(data.get("sourceType")).isEqualTo(sourceType);
    }
}
