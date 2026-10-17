#!/usr/bin/env bash
# 테스트 서버에서 실행 — 시나리오 규모만큼 방·참가자를 테스트 RDS에 넣는다.
# 사용: RDS_HOST=<엔드포인트> ./seed.sh <시나리오> <run_id>
# 비밀번호는 ~/.pgpass(host:5432:runiverse:admin_dev:<비밀번호>, chmod 600)에서 읽는다.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$(dirname "${SCRIPT_DIR}")/sql"
source "${SCRIPT_DIR}/presets.sh"

if [ $# -ne 2 ]; then
  echo "사용: RDS_HOST=<엔드포인트> $0 <시나리오> <run_id>" >&2
  exit 1
fi
: "${RDS_HOST:?RDS_HOST(테스트 RDS 엔드포인트)를 지정하세요}"
SCENARIO="$1"
RUN_ID="$2"
load_preset "${SCENARIO}"

if [ ! -f "${HOME}/.pgpass" ]; then
  echo "~/.pgpass가 없습니다 — RUNBOOK의 준비 단계를 먼저 하세요" >&2
  exit 1
fi

echo "시드: scenario=${SCENARIO} run_id=${RUN_ID} rooms=${ROOMS} runners=$((ROOMS * 4))"
sudo docker run --rm \
  -v "${SQL_DIR}:/sql:ro" \
  -v "${HOME}/.pgpass:/root/.pgpass:ro" \
  postgres:16-alpine \
  psql "host=${RDS_HOST} port=5432 user=admin_dev dbname=runiverse sslmode=require" \
  -v run_id="${RUN_ID}" -v rooms="${ROOMS}" -f /sql/seed.sql
