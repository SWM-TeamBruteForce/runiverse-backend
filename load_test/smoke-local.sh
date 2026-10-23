#!/usr/bin/env bash
# 로컬 smoke — 스크립트가 끝까지 도는지만 본다(성능 수치는 의미 없다).
# e2e용 docker compose 스택(postgres·redis·localstack·app)을 그대로 띄우고
# 시드 → k6(도커) → 상태 저장소 메트릭 출력 → 정리 순서로 진행한다.
#
# 이미지가 없으면 먼저 빌드한다:
#   (cd running-service && ./gradlew bootJar && docker build -t runiverse-backend:e2e .)
# 환경 변수: RUN_ID(1) ROOMS(1) RUN_SECONDS(120) KEEP_STACK(0 — 1이면 끝나도 스택을 남긴다)
#   시나리오 변수(SCENARIO·START_SPREAD_SECONDS·FINISH_SPREAD_SECONDS·STRICT)와
#   K6_OUT·K6_PROMETHEUS_RW_*는 설정돼 있을 때만 k6에 넘긴다
set -Eeuo pipefail

LOAD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "${LOAD_DIR}")"
COMPOSE_FILE="${ROOT_DIR}/e2e_test/compose.yml"
ENV_FILE="${ROOT_DIR}/e2e_test/.env.test"
PROJECT="runiverse-loadtest"
BASE_URL="http://localhost:8080/api/v1"

export E2E_IMAGE="${E2E_IMAGE:-runiverse-backend:e2e}"
export RUN_ID="${RUN_ID:-1}"
export ROOMS="${ROOMS:-1}"
export RUN_SECONDS="${RUN_SECONDS:-120}"
KEEP_STACK="${KEEP_STACK:-0}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:latest}"

export LOAD_TEST_DIR="${LOAD_DIR}"

compose() {
  docker compose --project-name "${PROJECT}" --file "${COMPOSE_FILE}" \
    --file "${LOAD_DIR}/compose.loadtest.yml" "$@"
}

cleanup() {
  local status=$?
  if [ "${status}" -ne 0 ]; then
    echo "===== 실패 — 앱 로그 (마지막 100줄) =====" >&2
    compose logs --tail 100 app >&2 || true
  fi
  if [ "${KEEP_STACK}" != "1" ]; then
    compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "${status}"
}
trap cleanup EXIT

if ! docker image inspect "${E2E_IMAGE}" >/dev/null 2>&1; then
  echo "이미지 ${E2E_IMAGE}가 없습니다 — 파일 상단의 빌드 명령을 먼저 실행하세요." >&2
  exit 1
fi

echo "===== 스택 기동 (${E2E_IMAGE}) ====="
compose up --detach --wait

echo "===== 앱 기동 대기 ====="
for attempt in $(seq 1 60); do
  if curl --fail --silent --output /dev/null "${BASE_URL}/actuator/health"; then
    break
  fi
  if [ "${attempt}" -eq 60 ]; then
    echo "앱이 기동되지 않았습니다." >&2
    exit 1
  fi
  sleep 2
done

# 앱 버전 헤더를 실은 연결은 최소 버전을 읽는다 — 행이 없으면 핸드셰이크가 500이다.
# E2E와 같은 SQL을 쓰며, 실패를 삼키면 k6가 엉뚱한 연결 실패로 깨지므로 바로 멈춘다
echo "===== 앱 버전 시드 ====="
compose exec -T postgres psql --quiet --username=runiverse --dbname=runiverse_e2e \
  --set ON_ERROR_STOP=1 --file=- < "${ROOT_DIR}/e2e_test/sql/app-versions.sql"

echo "===== 시드 (run_id=${RUN_ID}, rooms=${ROOMS}) ====="
compose exec -T postgres psql --quiet --username=runiverse --dbname=runiverse_e2e \
  -v run_id="${RUN_ID}" -v rooms="${ROOMS}" --file=- < "${LOAD_DIR}/sql/seed.sql"

# 값은 출력하지 않는다
JWT_SECRET="$(grep '^JWT_ACCESS_SECRET=' "${ENV_FILE}" | cut -d= -f2-)"
export JWT_SECRET

echo "===== k6 (${RUN_SECONDS}s) ====="
k6_status=0
docker run --rm --network "${PROJECT}_default" \
  --volume "${LOAD_DIR}/k6:/scripts:ro" \
  --env JWT_SECRET --env RUN_ID --env ROOMS --env RUN_SECONDS \
  --env SCENARIO --env STORE --env START_SPREAD_SECONDS --env FINISH_SPREAD_SECONDS --env STRICT \
  --env K6_PROMETHEUS_RW_SERVER_URL --env K6_PROMETHEUS_RW_TREND_STATS \
  --env WS_URL="ws://app:8080/api/v1/ws/running" \
  "${K6_IMAGE}" run ${K6_OUT:+--out "${K6_OUT}"} /scripts/running.js || k6_status=$?

echo "===== 상태 저장소 메트릭 ====="
curl --silent "${BASE_URL}/actuator/prometheus" \
  | grep -E '^runiverse_running_state_(access_seconds|loaded_items)_(count|sum)' || true

echo "===== DB 결과 ====="
compose exec -T postgres psql --username=runiverse --dbname=runiverse_e2e -c "
  SELECT r.running_room_id, r.status AS room_status,
         count(DISTINCT rec.running_record_id) AS records,
         (SELECT count(*) FROM running_combos c WHERE c.running_room_id = r.running_room_id) AS combos
  FROM running_rooms r
  LEFT JOIN running_records rec ON rec.running_room_id = r.running_room_id
  WHERE r.running_room_id >= 900000000
  GROUP BY r.running_room_id, r.status
  ORDER BY r.running_room_id;"

exit "${k6_status}"
