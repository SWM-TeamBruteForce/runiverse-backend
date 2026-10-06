#!/usr/bin/env bash
# 배포될 도커 이미지를 그대로 띄우고 E2E를 돌린 뒤 반드시 정리한다.
# 로컬과 GitHub Actions가 같은 경로를 타도록 양쪽 모두 이 스크립트만 부른다.
set -Eeuo pipefail
E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${E2E_DIR}/docker-compose.yml"
# 래퍼는 새로 만들지 않고 running-service 것을 빌려 쓴다
GRADLEW="$(dirname "${E2E_DIR}")/running-service/gradlew"
# compose의 ${E2E_IMAGE} 치환은 셸 환경변수만 읽는다 (.env.test는 컨테이너 안으로만 들어간다).
# CI는 워크플로가 ECR 태그를 넣어주고, 로컬은 직접 빌드한 기본 태그로 떨어진다.
export E2E_IMAGE="${E2E_IMAGE:-runiverse-backend:e2e}"
# 테스트 코드가 읽을 값 — 여기서 한 번만 정하고 아래 헬스체크와 공유한다
export E2E_BASE_URL="${E2E_BASE_URL:-http://localhost:8080/api/v1}"
export E2E_APP_CONTAINER="${E2E_APP_CONTAINER:-runiverse-e2e-app}"
readonly HEALTH_URL="${E2E_BASE_URL}/actuator/health"
readonly STARTUP_RETRIES=60
readonly STARTUP_INTERVAL=2
compose() {
  docker compose --file "${COMPOSE_FILE}" "$@"
}
# 실패해도 컨테이너를 남기지 않되, 지우기 전에 원인 파악용 로그를 뽑는다
cleanup() {
  local status=$?
  if [ "${status}" -ne 0 ]; then
    echo "===== 실패 — 앱 로그 (마지막 200줄) =====" >&2
    compose logs --tail 200 app >&2 || true
  fi
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  # 정리 결과가 아니라 원래 실패 원인의 종료 코드를 돌려줘야 CI가 빨간불이 된다
  exit "${status}"
}
trap cleanup EXIT
echo "===== E2E 스택 기동 (${E2E_IMAGE}) ====="
# --wait: postgres·redis의 healthcheck가 통과할 때까지 블로킹한다
compose up --detach --wait
# 앱 이미지(temurin jre)에 curl이 있다고 보장할 수 없어 준비 확인은 호스트에서 한다
echo "===== 앱 기동 대기 (${HEALTH_URL}) ====="
for attempt in $(seq 1 "${STARTUP_RETRIES}"); do
  if curl --fail --silent --output /dev/null "${HEALTH_URL}"; then
    echo "앱이 응답합니다. (${attempt}회차)"
    break
  fi
  # 컨테이너가 이미 죽었다면 더 기다릴 이유가 없다
  if [ "$(docker inspect --format '{{.State.Running}}' "${E2E_APP_CONTAINER}" 2>/dev/null)" != "true" ]; then
    echo "앱 컨테이너가 실행 중이 아닙니다." >&2
    exit 1
  fi
  if [ "${attempt}" -eq "${STARTUP_RETRIES}" ]; then
    echo "앱이 ${STARTUP_RETRIES}회 시도 안에 기동되지 않았습니다." >&2
    exit 1
  fi
  sleep "${STARTUP_INTERVAL}"
done
echo "===== E2E 테스트 실행 ====="
"${GRADLEW}" --project-dir "${E2E_DIR}" test --no-daemon "$@"
