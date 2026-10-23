# 서버 측정 실행 순서

환경: 테스트 서버(앱) · Redis EC2 · 테스트 RDS · 모니터링 서버(ES·Kibana·Prometheus·Grafana) · k6 EC2 — 모두 같은 VPC.
시나리오 규모는 `scripts/presets.sh` 한곳에서 정한다.

## 0. 준비 (한 번만)

1. `load_test/` 전체를 테스트 서버와 k6 EC2의 `~/load_test`로 복사한다.
   ```bash
   # 내 PC(WSL), ~/swmaestro에서
   for host in <테스트 서버 IP> <k6 EC2 IP>; do
     scp -r -i ./bastion-key-runiverse-dev.pem \
       -o ProxyCommand="ssh -i ./bastion-key-runiverse-dev.pem -W %h:%p ec2-user@<bastion IP>" \
       runiverse-backend/load_test "ubuntu@${host}:~/"
   done
   ```
2. 테스트 서버 — RDS 비밀번호를 `~/.pgpass`에 둔다(시드할 때 묻지 않게).
   ```bash
   read -rs -p "admin_dev 비밀번호: " PW; echo
   printf '%s:5432:runiverse:admin_dev:%s\n' "<RDS 엔드포인트>" "$PW" > ~/.pgpass; unset PW
   chmod 600 ~/.pgpass
   ```
3. k6 EC2 — JWT 비밀값이 `~/k6/.jwt`에 있어야 한다(`wc -c ~/k6/.jwt`가 32 이상).
4. 모니터링 서버 보안 그룹 — 9090 인바운드, 소스 k6 EC2 보안 그룹(`Prometheus remote write from k6`).
5. Grafana에 `monitoring/grafana-loadtest-dashboard.json`을 다시 가져온다(k6 구역 추가됨).

## 1. 한 회차 실행

시드와 k6는 **같은 시나리오·같은 run_id**여야 한다. run_id는 회차마다 새 값을 쓴다.
RDS의 `app_versions`에 `ANDROID` 행이 있고 최소 버전이 `1.1.0`(k6가 싣는 앱 버전 헤더) 이하인지 먼저 본다 — 아니면 핸드셰이크가 전부 500·403이다.

```bash
# 테스트 서버
RDS_HOST=<RDS 엔드포인트> ~/load_test/scripts/seed.sh load-100 11

# k6 EC2
APP_HOST=<테스트 서버 IP> PROM_HOST=<모니터링 서버 IP> ~/load_test/scripts/k6-run.sh load-100 redis 11
```

요약은 k6 EC2의 `~/k6/results/<시나리오>-<store>-run<id>.json`에 남는다.

## 2. run_id 배정

| 시나리오 | 규모 | redis 1·2·3회 | db 1·2·3회 |
|---|---|---|---|
| load-100 | 25방 · 10분 | 11 · 21 · 31 | 51 · 61 · 71 |
| load-400 | 100방 · 10분 | 12 · 22 · 32 | 52 · 62 · 72 |
| load-1000 | 250방 · 10분 | 13 · 23 · 33 | 53 · 63 · 73 |
| start-storm | 100방 동시 시작 · 5분 | 14 · 24 · 34 | 54 · 64 · 74 |
| finish-storm | 100방 · 2분에 걸쳐 시작 · 1분 안에 종료 | 15 · 25 · 35 | 55 · 65 · 75 |
| redis-failure | 100방 · 10분 · 5분쯤 Redis 재시작 | 16 · 26 · 36 | (db는 생략 가능) |

회차 사이에는 2~3분 쉬어 앞 회차의 종료 처리·GC가 다음 회차에 섞이지 않게 한다.

**앱을 (재)기동한 직후에는 워밍업 회차를 먼저 돌리고 결과를 버린다** — `load-400`을 run_id 90번대(예: redis 91, db 95)로 한 번. 1회차 run 11(100명)이 run 12(400명)보다 느렸던 원인이 JVM 워밍업이었다. 프로필을 바꿔 앱을 다시 띄우면 매번 해당한다.

## 2-1. DB 프로필로 바꾸기 (`state-db`)

러닝 중 상태 4종을 Redis 대신 테스트 RDS에 둔다(`infrastructure/persistence/running/state/*DbAdapter`).
pub/sub·토큰·쿨다운은 그대로 Redis다. 같은 이미지에서 프로필만 바꿔 두 저장소를 비교한다.

1. 이미지 만들기·옮기기 (내 PC)
   ```bash
   cd ~/swmaestro/runiverse-backend/running-service
   ./gradlew bootJar && docker build -t runiverse-backend:state-db .
   docker save runiverse-backend:state-db | gzip > /tmp/state-db.tar.gz
   cd ~/swmaestro
   scp -i ./bastion-key-runiverse-dev.pem \
     -o ProxyCommand="ssh -i ./bastion-key-runiverse-dev.pem -W %h:%p ec2-user@<bastion IP>" \
     /tmp/state-db.tar.gz ubuntu@<테스트 서버 IP>:~/
   ```
2. 테스트 서버 — 이미지 적재, 테이블 생성
   ```bash
   gunzip -c ~/state-db.tar.gz | sudo docker load
   sudo docker run --rm -v "$HOME/load_test/sql:/sql:ro" -v "$HOME/.pgpass:/root/.pgpass:ro" postgres:16-alpine \
     psql "host=<RDS 엔드포인트> user=admin_dev dbname=runiverse sslmode=require" -f /sql/state-db-schema.sql
   ```
3. 테스트 서버 — 앱을 프로필 지정으로 다시 띄운다(`.env`는 그대로, 프로필만 덮어쓴다)
   ```bash
   PROFILE=state-db   # Redis 프로필로 돌아갈 때는 PROFILE=default
   sudo docker rm -f runiverse-backend
   sudo docker run -d --name runiverse-backend --restart unless-stopped --network host \
     --env-file /opt/runiverse/.env -e SPRING_PROFILES_ACTIVE="${PROFILE}" \
     -v /var/log/runiverse:/app/logs runiverse-backend:state-db
   for i in $(seq 1 30); do curl -sf localhost:8080/api/v1/actuator/health >/dev/null && echo UP && break; sleep 5; done
   ```
4. 워밍업(`load-400`, run 95, 버림) 뒤 db 회차(51~56)를 돌린다. k6는 `k6-run.sh <시나리오> db <run_id>`.

**공정성:** 1회차 Redis 결과(run 11~17)는 배포 이미지(ECR)로 쟀다. db와 같은 코드로 비교하려면 2회차 Redis(21~26)는 이 실험 이미지를 `PROFILE=default`로 띄워 잰다.

## 3. Redis 장애 시나리오

k6를 `redis-failure`로 시작하고 약 5분 뒤 Redis EC2에서 Redis를 재시작한다(설치 방식에 맞게 하나).
```bash
sudo docker restart <redis 컨테이너>     # 도커로 띄웠다면
sudo systemctl restart redis-server     # 패키지로 설치했다면
```
재시작 시각을 적어 두고 Grafana에서 그 시각 전후를 본다.
- 상태 접근 실패(`result=failure`)가 언제 시작해서 언제 멈췄나
- k6 `에러 · 의도치 않은 끊김`, 종료 완료율
- Kibana에서 `labels.env : "test" and log.level : "ERROR"`

## 4. 회차마다 남길 것

- k6 요약 JSON
- Grafana 화면(회차 시간 범위로 맞춰서) — 상태 저장소 p99, 1,000개당 읽기 시간, 커넥션, CPU·힙, k6 왕복
- 테스트 RDS Performance Insights(db 프로필일 때) — 대기 이벤트·상위 쿼리
- 테스트 서버·Redis EC2 CPU 크레딧(t 계열이면)

## 5. 끝나면

1. 테스트 서버 앱을 원래 설정으로 되돌린다.
   ```bash
   sudo cp /opt/runiverse/.env.bak-before-loadtest /opt/runiverse/.env
   # 앱 컨테이너를 로그 마운트 붙여 다시 띄운다
   ```
2. 측정 자원을 지운다 — 테스트 RDS, k6 EC2, 모니터링 서버(결과 저장 후), 관련 보안 그룹.
3. 테스트 S3 버킷에 쌓인 GPS 트랙 파일을 정리한다(측정 날짜 기준).
