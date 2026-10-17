#!/usr/bin/env bash
# k6 EC2에서 실행 — 시나리오를 돌리고 결과를 Prometheus와 요약 파일로 남긴다.
# 사용: APP_HOST=<테스트 서버 IP> PROM_HOST=<모니터링 서버 IP> ./k6-run.sh <시나리오> <store> <run_id>
#   store: redis | db — 지금 앱이 어떤 상태 저장소로 떠 있는지(결과 태그용)
# JWT 비밀값은 JWT_FILE(기본 ~/k6/.jwt)에서 읽는다. 시드는 같은 시나리오·run_id로 먼저 넣어야 한다.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K6_DIR="$(dirname "${SCRIPT_DIR}")/k6"
source "${SCRIPT_DIR}/presets.sh"

if [ $# -ne 3 ]; then
  echo "사용: APP_HOST=<IP> PROM_HOST=<IP> $0 <시나리오> <redis|db> <run_id>" >&2
  exit 1
fi
: "${APP_HOST:?APP_HOST(테스트 서버 IP)를 지정하세요}"
: "${PROM_HOST:?PROM_HOST(모니터링 서버 IP)를 지정하세요}"
JWT_FILE="${JWT_FILE:-${HOME}/k6/.jwt}"
RESULT_DIR="${RESULT_DIR:-${HOME}/k6/results}"

export SCENARIO="$1"
export STORE="$2"
export RUN_ID="$3"
load_preset "${SCENARIO}"

if [ ! -s "${JWT_FILE}" ]; then
  echo "${JWT_FILE}이 없습니다 — RUNBOOK의 준비 단계를 먼저 하세요" >&2
  exit 1
fi
mkdir -p "${RESULT_DIR}"
SUMMARY="${SCENARIO}-${STORE}-run${RUN_ID}.json"

echo "k6: scenario=${SCENARIO} store=${STORE} run_id=${RUN_ID} rooms=${ROOMS} runners=$((ROOMS * 4))" \
     "run=${RUN_SECONDS}s start_spread=${START_SPREAD_SECONDS}s finish_spread=${FINISH_SPREAD_SECONDS:-each}"

JWT_SECRET="$(cat "${JWT_FILE}")"
export JWT_SECRET

# sudo는 환경 변수를 지운다 — 값은 -e 이름=값으로 직접 넘기고, 비밀값(JWT)만 preserve-env로 살린다.
# 요약 파일을 결과 디렉터리에 쓸 수 있게 현재 사용자로 돌린다(k6 이미지 기본 사용자는 다르다)
sudo --preserve-env=JWT_SECRET docker run --rm --network host \
  --user "$(id -u):$(id -g)" \
  -v "${K6_DIR}:/scripts:ro" \
  -v "${RESULT_DIR}:/results" \
  -e JWT_SECRET \
  -e SCENARIO="${SCENARIO}" -e STORE="${STORE}" -e RUN_ID="${RUN_ID}" \
  -e ROOMS="${ROOMS}" -e RUN_SECONDS="${RUN_SECONDS}" \
  -e START_SPREAD_SECONDS="${START_SPREAD_SECONDS}" -e STRICT="${STRICT}" \
  ${FINISH_SPREAD_SECONDS:+-e FINISH_SPREAD_SECONDS="${FINISH_SPREAD_SECONDS}"} \
  -e WS_URL="ws://${APP_HOST}:8080/api/v1/ws/running" \
  -e K6_PROMETHEUS_RW_SERVER_URL="http://${PROM_HOST}:9090/api/v1/write" \
  -e K6_PROMETHEUS_RW_TREND_STATS="p(50),p(99),max" \
  grafana/k6:latest run --out experimental-prometheus-rw \
  --summary-export "/results/${SUMMARY}" /scripts/running.js

echo "요약: ${RESULT_DIR}/${SUMMARY}"
