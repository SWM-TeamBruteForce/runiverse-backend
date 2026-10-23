// 식별자 규칙 — sql/seed.sql과 반드시 같아야 한다.

const ROOM_BASE = 900000000;
export const RUNNERS_PER_ROOM = 4;

export function userIdOf(userIndex) {
  return `00000000-0000-7000-8000-${String(userIndex).padStart(12, '0')}`;
}

export function roomIdOf(runId, userIndex) {
  return ROOM_BASE + runId * 10000 + Math.floor(userIndex / RUNNERS_PER_ROOM);
}

// 방 안에서의 자리 — 역할(나란히 달리기·신호 대기·일시정지)을 고르는 데 쓴다
export function slotOf(userIndex) {
  return userIndex % RUNNERS_PER_ROOM;
}
