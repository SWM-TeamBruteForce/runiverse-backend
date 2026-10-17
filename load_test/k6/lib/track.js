// 가상 좌표 생성기 — 1초마다 점 하나, 북쪽 직선 주행.
// 위치는 호출 횟수가 아니라 벽시계 경과로 정한다(일시정지·재연결로 틱이 빠져도 속도가 유지된다).
// 서버 트랙 필터(정확도 ≤ 50m, 속도 ≤ 12m/s)를 통과하는 값만 만든다.

const METERS_PER_DEGREE_LAT = 111320;
const BASE_LAT = 37.4979; // 강남역
const BASE_LON = 127.0276;
const RUN_SPEED = 3.0; // m/s — 약 5'33"/km
const ACCURACY_METERS = 5;
const TZ_OFFSET_MS = Number(__ENV.TZ_OFFSET_MINUTES || 540) * 60 * 1000;

// 서버는 recordedAt을 APP_TIME_ZONE의 오프셋 없는 LocalDateTime으로 읽는다
function localDateTime(epochMs) {
  return new Date(epochMs + TZ_OFFSET_MS).toISOString().slice(0, 19);
}

export function createTrack(roomOrdinal, lane) {
  return {
    // 방끼리 좌표가 겹치지 않게 경도를 방마다 약 90m씩 민다
    baseLon: BASE_LON + roomOrdinal * 0.001,
    laneMeters: lane * 3,
    distance: 0,
    sequence: 0,
    lastMs: null,
  };
}

// standing이면 제자리(GPS 흔들림 ±1m), 아니면 RUN_SPEED로 전진한다
export function nextPoint(track, nowMs, standing) {
  if (track.lastMs !== null && !standing) {
    track.distance += RUN_SPEED * (nowMs - track.lastMs) / 1000;
  }
  track.lastMs = nowMs;

  const jitter = standing ? (Math.random() - 0.5) * 2 : 0;
  const latitude = BASE_LAT + (track.distance + jitter) / METERS_PER_DEGREE_LAT;
  const longitude = track.baseLon
    + track.laneMeters / (METERS_PER_DEGREE_LAT * Math.cos(latitude * Math.PI / 180));

  return {
    sequence: track.sequence++,
    latitude,
    longitude,
    accuracyMeters: ACCURACY_METERS,
    recordedAt: localDateTime(nowMs),
    altitudeMeters: 30,
    speedMetersPerSecond: standing ? 0 : RUN_SPEED,
    headingDegrees: 0,
  };
}

// 일시정지 동안 흘러간 시간만큼 전진하지 않도록 기준 시각만 옮긴다
export function holdPosition(track, nowMs) {
  track.lastMs = nowMs;
}
