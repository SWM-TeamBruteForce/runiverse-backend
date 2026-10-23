// 러닝 WebSocket 부하 시나리오 — VU 1개 = 러너 1명.
// RUNNING_START → 1초마다 좌표 생성, 10초마다 RUNNING_LOCATION_UPDATE → RUNNING_FINISH(forced=true)
// 방 안 자리(slot)마다 역할이 다르다.
//   0, 1: 나란히 달리기(3m 간격) — 콤보가 쌓인다
//   2   : 신호 대기 — 중간에 제자리에 서 있는다
//   3   : 일시정지 후 재개, 이어서 연결을 끊고 재연결(마지막 배치 재전송 포함)
//
// 필수 환경 변수: JWT_SECRET(서버의 JWT_ACCESS_SECRET과 같은 값)
// 선택:
//   WS_URL, RUN_ID, ROOMS, RUN_SECONDS, TZ_OFFSET_MINUTES
//   SCENARIO              결과 태그용 이름(smoke·load-step·start-storm·finish-storm·redis-failure)
//   STORE                 결과 태그용 상태 저장소(redis·db) — 서버 메트릭의 store 태그와 맞춘다
//   START_SPREAD_SECONDS  방 시작을 이 시간에 고르게 퍼뜨린다(0이면 모든 방이 동시에 시작)
//   FINISH_SPREAD_SECONDS 지정하면 각자 RUN_SECONDS를 뛰지 않고, 모든 방이
//                         "시작 분산 + RUN_SECONDS" 시점부터 이 시간 안에 차례로 종료한다
//   STRICT                false면 에러가 있어도 실패로 보지 않는다(장애 시나리오용)

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { mintAccessToken } from './lib/token.js';
import { roomIdOf, slotOf, userIdOf, RUNNERS_PER_ROOM } from './lib/ids.js';
import { createTrack, holdPosition, nextPoint, startPause } from './lib/track.js';

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/api/v1/ws/running';
const JWT_SECRET = __ENV.JWT_SECRET;
const RUN_ID = Number(__ENV.RUN_ID || 1);
const ROOMS = Number(__ENV.ROOMS || 1);
const RUN_SECONDS = Number(__ENV.RUN_SECONDS || 120);
const SCENARIO = __ENV.SCENARIO || 'smoke';
const STORE = __ENV.STORE || 'redis';
const START_SPREAD_SECONDS = Number(__ENV.START_SPREAD_SECONDS || 0);
const FINISH_TOGETHER = __ENV.FINISH_SPREAD_SECONDS !== undefined;
const FINISH_SPREAD_SECONDS = Number(__ENV.FINISH_SPREAD_SECONDS || 0);
const STRICT = __ENV.STRICT !== 'false';
// 서버가 앱 버전 헤더가 없는 연결을 403으로 막는다 — 대상 서버의 안드로이드 최소 버전 이상이어야 한다
const APP_VERSION_HEADERS = { 'X-App-Platform': 'ANDROID', 'X-App-Version': '1.1.0' };

const BATCH_SECONDS = 10;
const PAUSE_SECONDS = 20;
const RECONNECT_GAP_SECONDS = 5;
// 서버가 연결을 끊었을 때 앱처럼 다시 붙는 최대 횟수(의도한 재연결은 세지 않는다)
const MAX_UNEXPECTED_RECONNECTS = 5;
const FINISH_TIMEOUT_MS = 30 * 1000;
// 정해진 시간보다 이만큼 넘게 걸리면 이상 상황으로 보고 끊는다
const OVERRUN_SECONDS = 90;

// 역할별 시점 — 각자 뛰는 시간에 대한 비율
const WAIT_FROM = 0.40;
const WAIT_UNTIL = 0.55;
const PAUSE_AT = 0.30;
const RECONNECT_AT = 0.65;

const startRtt = new Trend('running_start_rtt', true);
const batchRtt = new Trend('running_batch_rtt', true);
const finishRtt = new Trend('running_finish_rtt', true);
const wsErrors = new Counter('running_ws_errors');
const comboUpdates = new Counter('running_combo_updates');
const finishTimeouts = new Counter('running_finish_timeouts');
const unexpectedDisconnects = new Counter('running_unexpected_disconnects');
const finishCompleted = new Rate('running_finish_completed');

const LAST_FINISH_SECONDS = START_SPREAD_SECONDS + RUN_SECONDS + FINISH_SPREAD_SECONDS;

export const options = {
  scenarios: {
    [SCENARIO]: {
      executor: 'per-vu-iterations',
      vus: ROOMS * RUNNERS_PER_ROOM,
      iterations: 1,
      maxDuration: `${LAST_FINISH_SECONDS + OVERRUN_SECONDS + 300}s`,
    },
  },
  // Prometheus로 보낼 때 회차·저장소별로 갈라 볼 수 있게 붙인다
  tags: { test_scenario: SCENARIO, store: STORE, run_id: String(RUN_ID) },
  thresholds: STRICT
    ? {
      running_ws_errors: ['count==0'],
      running_finish_completed: ['rate==1'],
      checks: ['rate==1'],
    }
    : {},
};

export function setup() {
  if (!JWT_SECRET) {
    throw new Error('JWT_SECRET이 비어 있습니다 — 서버의 JWT_ACCESS_SECRET과 같은 값을 넣으세요');
  }
  // 모든 VU가 같은 기준 시각으로 시작·종료 시점을 계산한다
  return { testStartMs: Date.now() };
}

// 방 순번을 0~1로 펴서 시작·종료를 방 단위로 고르게 퍼뜨린다 — 같은 방 4명은 같이 움직인다
function roomFraction(roomOrdinal) {
  return ROOMS > 1 ? roomOrdinal / (ROOMS - 1) : 0;
}

function send(socket, event, data) {
  socket.send(JSON.stringify({ event, data }));
}

function flush(socket, runner) {
  if (runner.buffer.length === 0) {
    return;
  }
  send(socket, 'RUNNING_LOCATION_UPDATE', { locations: runner.buffer });
  runner.lastBatch = runner.buffer;
  runner.buffer = [];
  runner.batchSentAt = Date.now();
}

function sendFinish(socket, runner) {
  flush(socket, runner);
  runner.finishSentAt = Date.now();
  send(socket, 'RUNNING_FINISH', { forced: true });
}

function abort(socket, runner, reason) {
  console.error(`[${runner.userId}] ${reason} — 연결을 끊습니다`);
  runner.aborted = true;
  socket.close();
}

// 1초마다 — 역할에 따라 상태를 바꾸고 점을 하나 쌓는다
function tick(socket, runner, connection) {
  const now = Date.now();
  const elapsed = (now - runner.startedAtMs) / 1000;
  const duration = runner.durationSeconds;

  if (elapsed > duration + OVERRUN_SECONDS) {
    abort(socket, runner, '제한 시간 초과');
    return;
  }
  if (runner.finishSentAt !== null) {
    if (now - runner.finishSentAt > FINISH_TIMEOUT_MS) {
      finishTimeouts.add(1);
      abort(socket, runner, 'RUNNING_FINISHED 응답 없음');
    }
    return;
  }
  if (!connection.started) {
    return;
  }
  if (elapsed >= duration) {
    sendFinish(socket, runner);
    return;
  }

  if (runner.slot === 3) {
    if (!runner.pauseDone && runner.pausedAt === null && elapsed >= duration * PAUSE_AT) {
      startPause(runner.track);
      flush(socket, runner);
      send(socket, 'RUNNING_PAUSE', {});
      runner.pausedAt = now;
    }
    if (runner.pausedAt !== null && now - runner.pausedAt >= PAUSE_SECONDS * 1000) {
      send(socket, 'RUNNING_RESUME', {});
      runner.pausedAt = null;
      runner.pauseDone = true;
    }
    if (!runner.reconnectDone && elapsed >= duration * RECONNECT_AT) {
      flush(socket, runner);
      runner.reconnectDone = true;
      runner.needReconnect = true;
      socket.close();
      return;
    }
  }

  if (runner.pausedAt !== null) {
    holdPosition(runner.track, now);
    return;
  }

  const standing = runner.slot === 2
    && elapsed >= duration * WAIT_FROM
    && elapsed < duration * WAIT_UNTIL;
  runner.buffer.push(nextPoint(runner.track, now, standing));
}

function onMessage(socket, runner, connection, raw) {
  const message = JSON.parse(raw);
  const now = Date.now();

  switch (message.event) {
    case 'RUNNING_STARTED':
      if (!connection.started) {
        startRtt.add(now - connection.startSentAt);
        connection.started = true;
        // 재연결 직후 마지막 배치를 다시 보낸다 — 서버가 sequence로 중복을 걸러야 한다
        if (connection.resendLastBatch && runner.lastBatch.length > 0) {
          send(socket, 'RUNNING_LOCATION_UPDATE', { locations: runner.lastBatch });
        }
      }
      break;
    case 'RUNNING_PROGRESS_UPDATED':
      if (message.data.userId === runner.userId && runner.batchSentAt !== null) {
        batchRtt.add(now - runner.batchSentAt);
        runner.batchSentAt = null;
      }
      break;
    case 'RUNNING_COMBO_UPDATED':
      comboUpdates.add(1);
      break;
    case 'RUNNING_FINISHED':
      if (runner.finishSentAt !== null) {
        finishRtt.add(now - runner.finishSentAt);
      }
      runner.finished = true;
      // 바로 끊으면 방 브로드캐스트가 닫힌 소켓에 부딪혀 서버에 전송 오류 WARN이 쌓인다
      socket.setTimeout(() => socket.close(), 1000);
      break;
    case 'ERROR':
      wsErrors.add(1, { code: message.data.code });
      console.warn(`[${runner.userId}] ERROR ${message.data.code} - ${message.data.message}`);
      // 시작이 거절되면 아무것도 보낼 수 없어 서버 유휴 제한(2분)까지 헛돈다 — 바로 멈춘다
      if (!connection.started) {
        abort(socket, runner, `RUNNING_START 거절(${message.data.code}) — 시드·run_id·앱 DB 설정을 확인하세요`);
      }
      break;
    default:
      break;
  }
}

function connect(runner, resendLastBatch) {
  const connection = { started: false, startSentAt: null, resendLastBatch };
  // 끊기 직전 배치의 응답은 오지 않는다 — 재연결 뒤 진행 알림과 짝지어 재지 않게 비운다
  runner.batchSentAt = null;
  // 끊긴 사이 보내지 못한 종료는 다시 보낸다
  runner.finishSentAt = null;
  const params = { headers: { Authorization: `Bearer ${runner.token}`, ...APP_VERSION_HEADERS } };

  const response = ws.connect(WS_URL, params, (socket) => {
    socket.on('open', () => {
      connection.startSentAt = Date.now();
      send(socket, 'RUNNING_START', { runningRoomId: runner.roomId });
      socket.setInterval(() => tick(socket, runner, connection), 1000);
      socket.setInterval(() => {
        if (connection.started && runner.pausedAt === null && runner.finishSentAt === null) {
          flush(socket, runner);
        }
      }, BATCH_SECONDS * 1000);
    });
    socket.on('message', (raw) => onMessage(socket, runner, connection, raw));
    socket.on('error', (e) => {
      wsErrors.add(1, { code: 'SOCKET_ERROR' });
      console.error(`[${runner.userId}] 소켓 오류 - ${e.error()}`);
    });
  });

  check(response, { 'WS 핸드셰이크 101': (r) => r && r.status === 101 });
}

export default function (data) {
  const userIndex = __VU - 1;
  const roomOrdinal = Math.floor(userIndex / RUNNERS_PER_ROOM);
  const slot = slotOf(userIndex);
  const userId = userIdOf(userIndex);

  // 방 단위로 시작을 늦춘다
  const startAtMs = data.testStartMs + roomFraction(roomOrdinal) * START_SPREAD_SECONDS * 1000;
  const waitMs = startAtMs - Date.now();
  if (waitMs > 0) {
    sleep(waitMs / 1000);
  }

  const startedAtMs = Date.now();
  const finishAtMs = FINISH_TOGETHER
    ? data.testStartMs
      + (START_SPREAD_SECONDS + RUN_SECONDS + roomFraction(roomOrdinal) * FINISH_SPREAD_SECONDS) * 1000
    : startedAtMs + RUN_SECONDS * 1000;

  const runner = {
    userId,
    roomId: roomIdOf(RUN_ID, userIndex),
    slot,
    token: mintAccessToken(JWT_SECRET, userId),
    track: createTrack(roomOrdinal, slot),
    startedAtMs,
    durationSeconds: (finishAtMs - startedAtMs) / 1000,
    buffer: [],
    lastBatch: [],
    batchSentAt: null,
    pausedAt: null,
    pauseDone: false,
    reconnectDone: false,
    needReconnect: false,
    unexpectedReconnects: 0,
    finishSentAt: null,
    finished: false,
    aborted: false,
  };

  connect(runner, false);
  while (!runner.finished && !runner.aborted) {
    if (!runner.needReconnect) {
      // 의도하지 않은 끊김 — 서버 재시작·장애. 앱처럼 잠시 뒤 다시 붙는다
      unexpectedDisconnects.add(1);
      runner.unexpectedReconnects += 1;
      if (runner.unexpectedReconnects > MAX_UNEXPECTED_RECONNECTS) {
        console.error(`[${userId}] 재연결 ${MAX_UNEXPECTED_RECONNECTS}회 초과 — 포기합니다`);
        break;
      }
    }
    runner.needReconnect = false;
    sleep(RECONNECT_GAP_SECONDS);
    connect(runner, true);
  }

  finishCompleted.add(runner.finished);
}
