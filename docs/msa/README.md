# 서비스 사이 구조 (MSA)

Runiverse에서 서비스가 나뉘는 부분과, 이 백엔드(running-service)가 그 구조에서 무엇을 맡고 무엇을 지키는지 적는다. 서비스 사이 이벤트를 새로 만들거나, Kafka·CDC를 건드리거나, AI 서버와 계약을 바꿀 때 여기서 시작한다.

| 문서 | 내용 |
|---|---|
| [outbox-inbox.md](outbox-inbox.md) | 이벤트를 보내고 받는 코드 규칙 — 아웃박스 쓰기, inbox 처리 순서, 새 이벤트 체크리스트 |
| [kafka.md](kafka.md) | Kafka 클러스터 — 환경 접두어, 토픽, TLS·SASL·ACL, 접속 설정 |
| [cdc.md](cdc.md) | 아웃박스 → Kafka 발행 — Debezium, Postgres 논리 복제·복제 슬롯·heartbeat, 운영·검증 |
| [api-spec.md](../api-spec.md) 9-5 | 이벤트 계약 — 토픽, key, 헤더, 본문 필드 (AI 서버와 함께 보는 문서) |
| `infra/kafka/connectors/` | Debezium 커넥터 설정 — `test-outbox.json` · `prod-outbox.json` (비밀번호·prod DB 주소는 서버 파일 참조만) |

## 구성

```text
 Flutter 앱
    │ HTTP · SSE · WebSocket
    ▼
   ALB
    │
 running-service (여러 대) ◀──── Redis pub/sub ────▶ (인스턴스 사이)
    │            ▲
    │ INSERT     │ 완료 이벤트 구독 (예정)
    ▼            │
 Postgres        │
 outbox_events   │
    │ WAL        │
    ▼            │
 Debezium ──▶ Kafka ──▶ AI 서버 ──▶ S3 (음악)
 (Connect)     ▲            │
               └────────────┘ 완료 이벤트
```

## 누가 무엇을 맡는가

| 서비스 | 맡는 것 | 갖는 데이터 |
|---|---|---|
| running-service | 앱 API 전부. 기록 카드 저장, 음악 생성 상태 | Postgres — 사용자·러닝·기록 카드의 정본 |
| AI 서버 | 음악 생성 (모델·추론 인프라 포함) | 자기 저장소(inbox 포함). 만든 음악은 S3에 올리고 key를 정한다 |
| Debezium (Kafka Connect) | 커밋된 아웃박스 행을 Kafka로 발행 | 없음 — 어디까지 보냈는지는 복제 슬롯과 Connect 오프셋 |
| Kafka | 서비스 사이 이벤트 전달 | 이벤트 7일 보관 |

- **데이터는 서비스마다 따로 갖는다** — AI 서버는 running-service DB를 읽지 않는다. 필요한 값은 이벤트에 실어 보낸다(요청 이벤트의 `running_summary`). running-service도 AI 서버 저장소를 모른다.
- **함께 쓰는 것은 S3뿐이다** — AI 서버는 사진을 `image_key`로 읽고, 음악을 올린 key를 완료 이벤트로 알려 준다.

## 통신 — 어디로 무엇이 지나가는가

| 사이 | 수단 | 규칙 |
|---|---|---|
| 앱 ↔ running-service | HTTP · SSE · WebSocket | [api-spec.md](../api-spec.md) · [api-convention.md](../api-convention.md) |
| running-service 인스턴스 ↔ 인스턴스 | Redis pub/sub | [architecture.md](../architecture.md) 「다중 인스턴스」 |
| running-service ↔ AI 서버 | **Kafka만** | 이 폴더 |

- **서비스 사이는 Kafka로만 주고받는다** — running-service는 AI 서버를 HTTP로 부르지 않는다. 한쪽이 멈춰도 다른 쪽은 이벤트를 남기고 계속 돈다.
- **서비스 사이에 Redis pub/sub을 쓰지 않는다** — 받는 쪽이 그 순간 없으면 사라진다. Kafka는 이벤트가 남아 돌아와서 이어 읽는다.
- **Kafka 이벤트는 running-service 한 대만 받는다** — 그룹 하나로 구독한다([kafka.md](kafka.md) 「토픽」). 완료를 앱에 실시간으로 알리게 되면, 받은 인스턴스가 커밋한 뒤 Redis pub/sub으로 앱 연결을 든 인스턴스에 넘긴다 — 기존 SSE 푸시와 같은 방식이다.
- **보내는 쪽은 잃지 않는 것을, 받는 쪽은 두 번 처리하지 않는 것을 맡는다** — 전달은 최소 한 번이다. running-service는 아웃박스로 보내고 inbox로 받는다([outbox-inbox.md](outbox-inbox.md)).

## 이벤트

계약은 [api-spec.md](../api-spec.md) 9-5가 정본이다.

| 토픽 (`{env}.` 생략) | 방향 | 보내는 방식 | 받는 쪽 중복 거르기 | 상태 |
|---|---|---|---|---|
| `record-card.music.requested` | running-service → AI | 아웃박스 + CDC | AI 서버 inbox (헤더 `id`) | test·prod CDC 동작 중 |
| `record-card.music.generated` | AI → running-service | AI 서버가 직접 발행 | running-service inbox(헤더 `id` = `inference_id`) + `GENERATING`인 행만 바꾸는 규칙 | test·prod 배포됨, AI 서버 헤더 `id` 반영 전 |

### 흐름 — 기록 카드 음악

1. 앱이 작성을 확정한다(9-2) — 카드·사진과 아웃박스 행이 한 트랜잭션에 커밋된다. 음악은 `GENERATING`이다.
2. Debezium이 WAL에서 그 행을 읽어 요청 토픽에 발행한다.
3. AI 서버가 inbox로 중복을 거르고 음악을 만들어 S3에 올린다.
4. AI 서버가 완료 토픽에 결과를 발행한다.
5. running-service 한 대가 받아 inbox로 거르고 음악 상태를 바꾼다.

## 환경

| 환경 | DB | Kafka | CDC |
|---|---|---|---|
| `dev` | 로컬 Postgres | 개발 클러스터 (`dev.*`) | 없음 |
| `test` | EC2에 직접 설치한 Postgres (Kafka와 같은 EC2) | 개발 클러스터 (`test.*`) | 커넥터 `test-outbox` |
| `prod` | RDS | 운영 클러스터 | 커넥터 `prod-outbox` |

- dev·test는 Kafka 클러스터 하나를 이름 접두어로 나눠 쓴다([kafka.md](kafka.md) 「이름」).
- **`KAFKA_ENV`는 붙는 DB를 따른다** — 로컬 앱이라도 test DB에 붙으면 `test`다([cdc.md](cdc.md)).

## 지금 상태

| 단계 | 상태 |
|---|---|
| 아웃박스 쓰기 (9-2) | 됨 |
| Kafka 클러스터 (개발·운영) | 됨 — 계정·토픽·ACL |
| CDC (test·prod) | 됨 — 커넥터 동작, 발행·재기동·heartbeat 검증, prod 슬롯 지연 경보 |
| 아웃박스 코드 배포 | 됨 — test 서버·prod 모두 배포된 앱의 카드가 Kafka까지 나감 |
| AI 서버 연동 | test 됨 — 카드 확정 → 아웃박스 → Debezium → Kafka → AI 서버 수신·음악 생성. prod 수신은 AI 쪽 확인. 요청 이벤트의 헤더 `id` inbox와 완료 이벤트의 헤더 `id`(= `inference_id`)는 AI 서버와 확인 전 |
| 완료 구독 + inbox | 구현·검증 됨 — 로컬 앱 → 개발 클러스터로 반영·중복·계약 위반·늦은 결과 확인. test·prod 배포됨 — 배포된 앱이 파티션을 배정받고 CA 마운트·`inbox_events` 확인. 실제 완료 이벤트 반영은 AI 서버가 헤더 `id`를 넣은 뒤 확인 — 11-23 로컬 앱(test DB·test 토픽)으로 실제 카드를 보내 AI의 완료 이벤트를 받았으나 헤더 이름이 `inference_id`이고 AI 엔드포인트가 없어 `FAILED`였다 — [outbox-inbox.md](outbox-inbox.md) 「받는 쪽」 「검증」 |

## 서비스나 이벤트를 더할 때

- **이벤트** — [outbox-inbox.md](outbox-inbox.md) 「새 이벤트를 더할 때」 체크리스트.
- **서비스** — 계정 `{env}.<서비스>`, 컨슈머 그룹 `{env}.<서비스>.<용도>`, 토픽·그룹 권한(이름 하나씩), 그 환경 클러스터의 CA 인증서([kafka.md](kafka.md)). 이 문서의 「누가 무엇을 맡는가」와 「이벤트」 표에 더한다.
