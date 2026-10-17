# 러닝 상태 저장소 부하 테스트 (k6)

러닝 중 상태(좌표·누적 거리·참가자 상태·콤보)를 EC2 Redis에 둘 때와 RDS에 둘 때의
성능·비용을 같은 부하로 비교한다. 배경과 지표는 PR #82의 "후속 작업: k6 과부하 테스트"를 따른다.

## 구성

| 경로 | 내용 |
|---|---|
| `sql/seed.sql` | 시작 시각이 지난 4인 MATCHED 방을 `rooms`개 만든다(매칭 흐름 생략) |
| `sql/cleanup.sql` | 시드가 만든 데이터(방·신청·기록·콤보·사용자)를 지운다 |
| `k6/running.js` | VU 1개 = 러너 1명. START → 10초마다 좌표 배치 → FINISH(forced) |
| `k6/lib/` | 식별자 규칙(`ids.js`), 토큰 서명(`token.js`), 가상 좌표(`track.js`) |
| `stub/weather-stub.js` | Open-Meteo 대역 — 외부 지연이 종료 응답에 섞이지 않게 한다 |
| `smoke-local.sh` | 로컬 smoke — e2e 스택 + 날씨 스텁으로 한 번에 돌린다 |
| `scripts/` | 서버 측정용 — 시나리오 규모(`presets.sh`), 시드(`seed.sh`), k6 실행(`k6-run.sh`) |
| `monitoring/` | Grafana 대시보드, 로컬 확인용 Prometheus 설정 |
| `RUNBOOK.md` | 서버 측정 실행 순서 — 준비, 회차 실행, run_id 배정, 장애 시나리오, 정리 |

방 안 자리별 역할: 0·1 나란히 달리기(콤보), 2 신호 대기(제자리), 3 일시정지·재개 후 재연결(마지막 배치 재전송).

## 식별자 규칙 (시드와 k6가 공유)

- 사용자 u: `00000000-0000-7000-8000-{u 12자리}` — 서버가 UUIDv7만 받는다
- 방: `900000000 + run_id * 10000 + u / 4`
- **회차마다 `run_id`를 바꾼다.** 같은 방 번호를 다시 쓰면 이전 회차의 Redis 키(TTL 7h)가 남아 있어 좌표가 중복으로 걸러진다.

## 로컬 smoke

스크립트가 끝까지 도는지만 본다. 로컬은 네트워크 왕복이 없어 수치는 비교에 쓰지 않는다.

```bash
(cd running-service && ./gradlew bootJar && docker build -t runiverse-backend:e2e .)
./load_test/smoke-local.sh                 # RUN_ID=1 ROOMS=1 RUN_SECONDS=120
KEEP_STACK=1 ./load_test/smoke-local.sh    # 끝나도 스택을 남긴다(로그 확인용)
```

통과 기준: `running_ws_errors` 0, `running_finish_completed` 100%, 방이 `FINISHED`이고 기록 4건.

## 테스트 서버에서 실행

1. 앱 설정 — `WEATHER_BASE_URI`를 스텁으로(`node stub/weather-stub.js`, 포트 8090), S3는 테스트 버킷으로 둔다.
   실제 날씨 API는 실패 시 재시도로 종료 한 건에 수 초를 더하고, 같은 방의 종료는 차례로 처리돼 그 지연이 쌓인다.
2. 시드 — RDS에 넣는다.
   ```bash
   psql "host=<RDS> dbname=<DB> user=<USER>" -v run_id=2 -v rooms=1 -f load_test/sql/seed.sql
   ```
3. k6 — 별도 EC2에서 돌린다. `JWT_SECRET`은 앱의 `JWT_ACCESS_SECRET`과 같은 값이다(출력하지 않는다).
   ```bash
   k6 run -e WS_URL=ws://<앱 주소>:8080/api/v1/ws/running -e JWT_SECRET="$JWT_SECRET" \
          -e RUN_ID=2 -e ROOMS=1 load_test/k6/running.js
   ```
4. 끝나면 `sql/cleanup.sql`로 정리한다.

## k6 지표

| 지표 | 뜻 |
|---|---|
| `running_start_rtt` | START 송신 → `RUNNING_STARTED` |
| `running_batch_rtt` | 좌표 배치 송신 → 본인 `RUNNING_PROGRESS_UPDATED` |
| `running_finish_rtt` | FINISH 송신 → `RUNNING_FINISHED` |
| `running_combo_updates` | 받은 `RUNNING_COMBO_UPDATED` 수 |
| `running_ws_errors` | 받은 `ERROR`(code 태그) + 소켓 오류 |
| `running_finish_completed` | 종료까지 마친 러너 비율 |

서버 쪽은 `/api/v1/actuator/prometheus`의 `runiverse_running_state_access_seconds`(store·target·phase·operation)와
`runiverse_running_state_loaded_items`를 본다.
