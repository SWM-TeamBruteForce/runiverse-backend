# CDC — 아웃박스 행을 Kafka로

커밋된 `outbox_events` 행을 Debezium이 Postgres WAL에서 읽어 Kafka에 발행하는 경로의 구성과 규칙이다. CDC를 건드리거나, 이벤트를 늘리거나, 새 환경에 붙일 때 본다. 설치 명령은 적지 않는다.

| 무엇 | 어디 |
|---|---|
| 아웃박스·heartbeat 테이블 (정본) | [erd.md](../erd.md) `outbox_events` · `cdc_heartbeats` |
| 아웃박스를 쓰는 코드 규칙 | [outbox-inbox.md](outbox-inbox.md) |
| 발행되는 이벤트 계약 (헤더·key·본문) | [api-spec.md](../api-spec.md) 9-5 |
| Kafka 클러스터·계정·권한 | [kafka.md](kafka.md) |
| 커넥터 설정 | `infra/kafka/connectors/{env}-outbox.json` |

## 구성

```text
running-service ──INSERT──▶ Postgres
                             outbox_events ─ WAL ─▶ 복제 슬롯 ─▶ Kafka Connect (systemd, Kafka EC2)
                             cdc_heartbeats ◀─┐                    └ Debezium Postgres 커넥터
                             (publication: 두 테이블, INSERT만)        └ Outbox Event Router
                                              └─ heartbeat 쿼리 (1분)        │ SASL_SSL ({env}.debezium)
                                                                             ▼
                                                               {env}.record-card.music.requested
```

- **환경마다 DB 하나에 커넥터 하나, 슬롯 하나다.** 커넥터 하나가 여러 환경의 DB를 읽지 않는다.
- **전달은 최소 한 번이다.** Connect는 어디까지 보냈는지를 주기적으로(60초) 기록하고, 그 뒤에야 슬롯 위치를 앞으로 민다. 재시작하면 마지막 기록 뒤의 행을 다시 보낸다 — 받는 쪽 inbox가 거른다([outbox-inbox.md](outbox-inbox.md)).
- **Connect·Kafka가 멈춰도 이벤트를 잃지 않는다.** 앱은 DB에만 쓰므로 카드 저장은 계속 성공하고, 슬롯이 WAL을 붙잡고 있다가 Connect가 돌아오면 이어서 보낸다. 대신 그동안 WAL이 쌓인다(아래 「복제 슬롯」).

### 환경

| | `test` | `prod` | `dev` |
|---|---|---|---|
| DB | EC2에 직접 설치한 PostgreSQL 17 — **Kafka와 같은 EC2** | RDS PostgreSQL 18 (단일 AZ) | 로컬 Postgres |
| Kafka | dev·test 공유 클러스터 | 운영 클러스터 | 공유 클러스터 |
| Connect 위치 | Kafka·DB EC2 (`t3.medium`) — DB에 `localhost`로 붙는다 | prod Kafka EC2 (`t3.medium`) — RDS에 VPC 안에서 붙는다. RDS 보안 그룹이 Kafka EC2 보안 그룹의 5432를 허용한다. `sslmode=require` | **없다** — VPC 안의 Connect가 로컬에 닿지 않는다 |
| 커넥터 | `test-outbox` | `prod-outbox` | — |
| 복제 슬롯 | `debezium_outbox_test` | `debezium_outbox_prod` | — |
| 논리 복제 설정 | `postgresql.conf` | 파라미터 그룹 `runiverse-backend-deploy-postgres18` | — |
| WAL 상한 (`max_slot_wal_keep_size`) | 5GB (루트 디스크 29G를 OS·Kafka 로그와 같이 쓴다) | 10GB (스토리지 100 GiB, 자동 조정 켜짐) | — |
| 슬롯 지연 알람 | 없음 — CloudWatch가 없다 | CloudWatch `prod-cdc-replication-slot-lag` | — |

- **test와 prod는 DB 종류가 달라 준비 방법이 다르다** — 아래 「Postgres 쪽」의 표를 환경별로 따른다.
- **test는 Kafka·DB·Connect가 한 EC2다** — WAL이 디스크를 채우면 Kafka도 같이 멈춘다.

### `KAFKA_ENV`는 기계가 아니라 DB를 따른다

아웃박스 `topic` 값은 행을 쓰는 앱의 `KAFKA_ENV`로 정해지고, 커넥터는 그 값대로 보낸다.

- **로컬에서 CDC까지 확인하려면 로컬 앱을 test DB에 붙이고(bastion 터널) `KAFKA_ENV=test`로 띄운다.** 로컬 Postgres에 쓴 행은 아무도 보내지 않는다.
- `dev`로 띄운 채 test DB에 쓰면 test 커넥터가 `dev.*` 토픽으로 보내려다 권한 오류로 멈추고, 재시작해도 같은 행에서 다시 멈춘다(행은 이미 WAL에 있다). **test DB의 토픽 CHECK는 `test` 토픽만 허용해** 이 경우를 저장 단계에서 막는다 — prod DB가 `prod`만 허용하는 것과 같다.
- 로컬 앱을 test DB에 붙일 때는 `JPA_DDL=none`으로 띄운다 — `update`면 브랜치 엔티티 기준으로 test DB의 다른 테이블이 바뀐다.

## Postgres 쪽

### 논리 복제

| | test (직접 설치) | prod (RDS) |
|---|---|---|
| 켜는 곳 | `wal_level=logical` | 사용자 정의 파라미터 그룹 `rds.logical_replication=1` — 기본 그룹은 고칠 수 없다 |
| 적용 | Postgres **재시작** (reload로는 안 바뀐다) | 인스턴스 **재부팅** — 그룹을 바꿔 끼운 **뒤에** 재부팅해야 한다(상태가 "동기화 중"이어야 적용된 것) |

- `max_replication_slots` · `max_wal_senders`는 기본값으로 충분하다.

### 커넥터 전용 DB 계정 `debezium`

- **슈퍼유저를 주지 않고, 앱 계정과 같이 쓰지 않는다.**

| 권한 | test | prod |
|---|---|---|
| 복제 | 계정 속성 `REPLICATION` | `rds_replication` 역할 (RDS는 속성을 직접 줄 수 없다) |
| `outbox_events` | `SELECT` | `SELECT` |
| `cdc_heartbeats` | `SELECT` `INSERT` `DELETE` | `SELECT` `INSERT` `DELETE` |

- 비밀번호는 Connect 서버의 파일에만 둔다(아래 「Kafka Connect」).

### publication `outbox_publication`

- **관리자가 미리 만든다** — 테이블 주인만 만들 수 있다. 커넥터는 만들지 않게 한다(`publication.autocreate.mode=disabled`) — 커넥터 계정에 주인 권한을 주지 않는다.
- **`outbox_events`와 `cdc_heartbeats` 두 테이블만 담는다** — 사용자·러닝 기록 같은 다른 테이블의 변경은 Debezium에 가지 않는다.
- **INSERT만 내보낸다(`publish = 'insert'`)** — 두 테이블 모두 의미 있는 변경은 INSERT뿐이다. 지난 행을 지워도 DELETE는 Debezium에 가지 않는다.
- 이벤트 종류를 늘려도 테이블이 같아 publication은 그대로다.

### 복제 슬롯 — 가장 조심할 것

- **슬롯은 Debezium이 확인한 위치 뒤의 WAL을 지우지 못하게 붙잡는다.** Connect·커넥터·Kafka가 멈추면 WAL이 쌓인다.
  - test: 디스크가 차면 DB와 Kafka가 함께 멈춘다 — 서비스 전체 장애다.
  - prod: 스토리지 자동 조정이 디스크를 늘려 버려 멈추는 대신 **비용이 조용히 늘고, 늘어난 스토리지는 줄일 수 없다.**
- **Debezium은 publication 안 변경을 처리할 때만 슬롯을 민다.** 카드가 만들어지지 않는 동안에도 다른 테이블 쓰기(RDS는 자체 쓰기도 있다)로 WAL은 늘어난다. 그래서 heartbeat를 두 겹으로 둔다.
  - **heartbeat 쿼리**(`heartbeat.action.query`)가 1분마다 `cdc_heartbeats`에 한 줄 쓰고 하루 지난 줄을 지운다(한 문장). 그 INSERT가 publication을 거쳐 Debezium에 돌아오며 위치가 앞으로 간다.
  - **heartbeat 토픽**(`{env}.debezium-heartbeat.outbox`)에 그 위치가 담긴 기록이 나가고, Connect가 오프셋을 저장한 뒤 슬롯이 밀린다.
  - `heartbeat.interval.ms`만 켜고 쿼리를 빼면 슬롯이 따라오지 않는다.
- **막는 장치**
  - 상한 `max_slot_wal_keep_size` — 넘으면 Postgres가 슬롯을 버리고 WAL을 지운다. 디스크는 지키지만 그 구간 이벤트는 Kafka로 나가지 못하고(행은 DB에 남는다 — 다시 보내는 절차는 아직 없다) 커넥터를 다시 만들어야 한다.
  - 알람 — 상한에 닿기 훨씬 전에 안다. prod는 CloudWatch(아래 「운영」), test는 아직 없다.
- **커넥터를 영구히 내리면 슬롯도 지운다** — 아무도 읽지 않는 슬롯이 WAL을 계속 붙잡는다. prod는 경보도 함께 끈다(슬롯이 사라지면 경보가 울린다).
- 슬롯 이름은 환경마다 고정한다 — 커넥터를 지웠다 다시 만들어도 같은 슬롯을 이어 쓴다.

## Debezium 커넥터 설정

이벤트 계약(9-5)이 이 설정으로 정해진다 — 바꾸면 받는 쪽이 깨진다. 설정 파일은 `infra/kafka/connectors/`에 있고, test와 prod는 DB 접속·슬롯·heartbeat 토픽 접두어만 다르다.

| 아웃박스 컬럼 | Kafka 메시지 | 설정 |
|---|---|---|
| `outbox_event_id` | 헤더 `id` | `table.field.event.id` |
| `aggregate_id` | key | `table.field.event.key` |
| `topic` | 토픽 | `route.by.field=topic`, `route.topic.replacement=${routedByValue}` — 기본값은 `outbox.event.`을 앞에 붙인다 |
| `payload` | value | `table.field.event.payload` |

- **key·value는 `StringConverter`로 문자열 그대로 내보낸다.**
  - JSON 변환기는 key에 따옴표를 붙이고, value(`jsonb`는 문자열로 읽힌다)를 한 번 더 감싼다.
  - payload를 펼치는 설정(`table.expand.json.payload`)은 `null` 필드를 뺀다 — "값이 없어도 키를 둔다"는 계약과 맞지 않는다.
  - `jsonb`는 키 순서를 바꾼다 — 받는 쪽은 키 순서에 기대지 않는다.
- **헤더에는 `id` 말고 `__debezium.context.*`도 온다** — 받는 쪽은 헤더를 이름 `id`로 찾는다.
- **`table.include.list`는 `outbox_events`만** — `cdc_heartbeats`는 publication에는 있지만 이벤트로 만들지 않는다.
- **아웃박스 변환기는 아웃박스 토픽에만 적용한다(predicate)** — heartbeat 기록은 변환하지 않는다. 변환이 빠지면 `outbox.public.outbox_events`로 쓰려다 권한 오류로 멈춘다(엉뚱한 곳으로 새지 않는다).
- **스냅샷은 하지 않는다(`snapshot.mode=no_data`)** — 슬롯을 만든 뒤 들어온 행만 보낸다. 그래서 새 환경에서는 **커넥터를 먼저 붙이고 아웃박스 코드를 배포한다** — 반대면 그 사이 카드의 음악 요청이 나가지 않는다.
- `tombstones.on.delete=false`.
- 프로듀서 중복 방지는 Connect 워커 설정에서 켠다([kafka.md](kafka.md) 「보내는 쪽은 중복 방지를 켠다」).

## Kafka Connect

| 항목 | 값 |
|---|---|
| 실행 | Kafka 4.1.2에 들어 있는 Connect를 systemd `kafka-connect`로, `kafka` 사용자, 힙 최대 1GB |
| 플러그인 | Debezium 3.7.1.Final Postgres 커넥터 — `/opt/kafka/plugins`. 아웃박스 변환기는 같은 폴더의 `debezium-connect-plugins` jar에 있다 |
| 워커 설정 | `/opt/kafka/config/connect-distributed-{env}.properties` (`root:kafka 640`) |
| 그룹·내부 토픽 | `{env}.debezium-connect`, `{env}.debezium-connect.configs` · `.offsets` · `.status` ([kafka.md](kafka.md)) |
| Kafka 계정 | `{env}.debezium` — 워커 자신, 이벤트 발행, 토픽 조회 모두 이 계정 |
| 시크릿 | `/opt/kafka/config/secrets/debezium-{env}-db.properties` — DB 비밀번호(prod는 RDS 주소도) |

- **REST API는 `127.0.0.1:8083`에서만 연다** — 인증이 없어 닿기만 하면 커넥터를 만들고 설정을 읽을 수 있다. 커넥터 등록·조회는 그 서버 안에서 한다.
- **비밀번호를 커넥터 설정에 직접 쓰지 않는다** — 커넥터 설정은 configs 토픽에 평문으로 저장되고 REST로도 보인다. 설정에는 `${file:...}` 참조만 두고 워커의 `FileConfigProvider`가 실행할 때 읽는다. 저장소의 설정 파일에도 참조만 있다.
- **워커 설정의 보안 항목은 세 번 쓴다** — 맨 위(워커 자신), `producer.`(이벤트 발행), `admin.`(토픽 조회). 앞의 것을 물려받지 않는다.
- 커넥터 설정을 바꿀 때는 같은 이름으로 `PUT /connectors/{name}/config` 한다 — 슬롯과 보낸 위치를 이어 쓴다. 파일 전체를 보내야 한다(바꿀 줄만 보내면 나머지 설정이 사라진다).

## 운영 — 상태 보는 법

| 무엇 | 어디서 | 정상 |
|---|---|---|
| 커넥터 상태 | Connect 서버에서 `GET /connectors/{env}-outbox/status` | 커넥터·태스크 모두 `RUNNING` |
| 슬롯 | DB의 `pg_replication_slots` — `active`, `wal_status`, `confirmed_flush_lsn`과 현재 WAL 위치의 차이(지연) | `active = t`, `reserved`, 지연 KB 단위 |
| heartbeat | `cdc_heartbeats`의 `max(created_at)` | 1분 이내 |
| prod 슬롯 지연 경보 | CloudWatch `prod-cdc-replication-slot-lag` — `AWS/RDS` `OldestReplicationSlotLag`(prod DB) 최대값이 15분 연속 1GB 초과, 지표가 끊겨도 경보 → SNS `runiverse-prod-alerts` | 정상(OK) |

- 경보가 오면 커넥터 상태 → Connect 서비스 → Kafka 순서로 본다. 슬롯 지연이 줄지 않고 계속 늘면 상한(10GB)에 닿기 전에 고친다.

## 검증 — 테스트가 못 보는 것

E2E에는 Kafka·Debezium이 없다. 새 환경에 붙이거나 설정을 바꾸면 직접 확인한다.

| 확인 | test | prod |
|---|---|---|
| 아웃박스 행 하나 → 메시지 하나 | ✅ 실제 카드 | ✅ 배포 전 검증용 행(확인 뒤 메시지·행 삭제) |
| 헤더 `id` = `outbox_event_id`, key 따옴표 없음, value는 JSON 그대로·`null` 유지, 토픽은 컬럼 값 그대로 | ✅ | ✅ |
| Connect를 멈춘 동안 만든 이벤트가 재기동 뒤 나간다 | ✅ | test와 같은 구성 |
| 카드가 없어도 슬롯 지연이 늘지 않는다(heartbeat) | ✅ | ✅ |
| DB `DELETE`가 Kafka로 나가지 않는다 | — | ✅ |
| 배포된 앱으로 만든 첫 카드 | ✅ test 서버 앱 → 9-2 → 메시지 | ✅ prod 앱 → 9-2 → 메시지 |
| AI 서버가 자기 계정·그룹으로 받아 음악을 만든다 | ✅ 실제 카드(로컬 앱 → test DB) | AI 쪽 |
| AI 서버 inbox가 헤더 `id`로 중복을 거른다 | AI 서버와 확인 전 | AI 서버와 확인 전 |

- 검증 메시지를 실제 토픽에 남기지 않는다 — 확인 뒤 `kafka-delete-records`로 지운다([kafka.md](kafka.md)).

## 새 환경에 CDC를 붙이는 순서

1. **DB** — 논리 복제 켜기 → `max_slot_wal_keep_size` → `outbox_events`·`cdc_heartbeats`(자동 생성이 아니면 손으로) → **앱 DB 계정에 `outbox_events` INSERT 권한**(손으로 만든 테이블은 만든 계정의 것이라, 기본 권한이 없으면 앱이 쓰지 못하고 9-2가 500으로 롤백된다. `has_table_privilege`로 확인) → 커넥터 계정 → publication
2. **Kafka** — [kafka.md](kafka.md) 「CDC를 붙일 때 Kafka에 필요한 것」
3. **Connect** — 플러그인, 워커 설정, 시크릿 파일, systemd, REST는 루프백만
4. **커넥터 등록** — `infra/kafka/connectors/{env}-outbox.json`
5. **검증** — 위 표
6. **알람** — 슬롯 지연
7. **앱 배포** — `KAFKA_ENV`를 먼저 넣고(기본값이 없어 없으면 기동하지 않는다), 커넥터를 붙인 뒤 배포한다

## 정하지 않은 것

1. **지난 아웃박스 행 정리** — 언제 지울지(지워도 publication이 INSERT만 내보내 Kafka로 나가지 않는다).
2. **상한을 넘어 버려진 구간 다시 보내기** — 행은 DB에 남지만 다시 보내는 절차가 없다.
3. **test 슬롯 지연 알람** — CloudWatch가 없어 `pg_replication_slots`를 조회해 내보내야 한다([metrics-convention.md](../metrics-convention.md)와 맞춘다).
4. ~~로컬에서 완료 이벤트 받기~~ — **정했다**: 로컬 루프백에 브로커의 VPC 주소를 별칭으로 붙이고 그 주소로 SSH 터널을 열어 개발 클러스터에 붙는다([kafka.md](kafka.md) 「로컬 앱으로 붙기」).
5. **로컬 dev에서 음악 요청을 보내지 않기** — 메일·푸시는 `local` 프로필이면 로그만 남긴다(`LoggingEmailAdapter`·`LoggingPushAdapter`). 아웃박스는 로컬 앱을 test DB에 붙여 CDC를 확인할 때 실제로 써야 해서, 끄는 기준을 프로필이 아니라 `KAFKA_ENV`에 둘지 함께 정한다.
6. **prod를 Multi-AZ로 바꿀 때** — 장애 조치에서 복제 슬롯이 넘어가는지 다시 본다.
7. **prod Debezium → RDS 인증서 확인** — 지금 `sslmode=require`라 암호화만 하고 RDS 서버 인증서·호스트 이름은 확인하지 않는다. Connect의 트러스트스토어는 Connect → Kafka 연결에만 쓰여 여기에 해당하지 않는다. prod Kafka EC2에 RDS CA 번들을 두고 `database.sslmode=verify-full`, `database.sslrootcert`로 바꾼다 — 접속 주소가 인증서 이름과 같아야 해서 RDS 엔드포인트 이름으로 붙는다. test는 같은 EC2의 `localhost` 연결이라 해당이 없다.
8. **앱(running-service) → RDS 연결의 `sslmode`** — 7과 같은 기준으로 점검한다.
