#!/usr/bin/env bash
# 시나리오별 규모 — seed.sh(테스트 서버)와 k6-run.sh(k6 EC2)가 같은 값을 쓰도록 한곳에 둔다.
# 사용: source presets.sh; load_preset <시나리오>
#   ROOMS                 방 수(러너 = 방 × 4)
#   RUN_SECONDS           각 러너가 뛰는 시간(종료 폭주는 기준 시간)
#   START_SPREAD_SECONDS  방 시작을 퍼뜨리는 시간(0 = 동시 시작)
#   FINISH_SPREAD_SECONDS 비어 있으면 각자 종료, 값이 있으면 이 시간 안에 모든 방이 종료
#   STRICT                false면 에러가 있어도 k6를 실패로 보지 않는다

load_preset() {
  unset FINISH_SPREAD_SECONDS
  STRICT=true
  case "$1" in
    smoke)         ROOMS=1;   RUN_SECONDS=120; START_SPREAD_SECONDS=0 ;;
    load-100)      ROOMS=25;  RUN_SECONDS=600; START_SPREAD_SECONDS=60 ;;
    load-400)      ROOMS=100; RUN_SECONDS=600; START_SPREAD_SECONDS=60 ;;
    load-1000)     ROOMS=250; RUN_SECONDS=600; START_SPREAD_SECONDS=60 ;;
    # 시작만 몰리게 한다 — 종료까지 같이 몰리면 400명 동시 종료가 되어 시작 측정이 묻힌다(run 14)
    start-storm)   ROOMS=100; RUN_SECONDS=300; START_SPREAD_SECONDS=0; FINISH_SPREAD_SECONDS=120 ;;
    finish-storm)  ROOMS=100; RUN_SECONDS=300; START_SPREAD_SECONDS=120; FINISH_SPREAD_SECONDS=60 ;;
    redis-failure) ROOMS=100; RUN_SECONDS=600; START_SPREAD_SECONDS=60; STRICT=false ;;
    *)
      echo "알 수 없는 시나리오: $1" >&2
      echo "가능: smoke load-100 load-400 load-1000 start-storm finish-storm redis-failure" >&2
      return 1
      ;;
  esac
  export ROOMS RUN_SECONDS START_SPREAD_SECONDS STRICT
  [ -n "${FINISH_SPREAD_SECONDS:-}" ] && export FINISH_SPREAD_SECONDS
  return 0
}
