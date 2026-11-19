# Kafka

서비스 사이 이벤트가 지나가는 Kafka 클러스터의 구성과, 그 구성 때문에 개발·운영에서 지켜야 할 것이다. 토픽·계정·권한을 더하거나 Kafka 클라이언트를 붙일 때 본다. 설치 명령은 적지 않는다 — 구성이 바뀌면 이 문서를 먼저 고친다.

| 무엇 | 어디 |
|---|---|
| 이벤트 계약 (토픽·본문 필드) | [api-spec.md](../api-spec.md) 9-5 |
| 아웃박스 → Kafka 발행 (Debezium) | [cdc.md](cdc.md) |
| 보내고 받는 코드 규칙 | [outbox-inbox.md](outbox-inbox.md) |

> 주소·비밀번호·키 파일은 이 문서에 적지 않는다. 앱의 접속 값은 Parameter Store로만 다룬다.

## 한눈에

```text
running-service ─┐
AI 서버 ─────────┼─ SASL_SSL :9092 (TLS + SCRAM, ACL로 권한 확인) ─▶ Kafka EC2 (노드 1대)
Debezium ────────┘   (Connect는 같은 EC2)                           ├ broker
                                                                    └ controller  ← 127.0.0.1:9093
                                                                       (SASL_PLAINTEXT · PLAIN, 같은 EC2 안만)
```

## 클러스터

| 항목 | 값 | 개발에 주는 의미 |
|---|---|---|
| 버전 | Kafka 4.1.2, Java 21 | 멱등 프로듀서는 토픽 `WRITE`만 있으면 된다(클러스터 `IDEMPOTENT_WRITE` 불필요) |
| 방식 | KRaft 결합 모드 — 한 프로세스가 broker·controller를 겸한다 | 노드가 하나다 (아래 「노드가 하나라서」) |
| 실행 | systemd `kafka`, 힙 512 MiB, 실패 시 10초 뒤 재시작 | 재시작하는 동안 모든 클라이언트가 잠깐 끊긴다 — 클라이언트는 재연결·재시도로 견딘다 |
| 토픽 자동 생성 | **꺼짐** | 이름을 잘못 쓰면 토픽이 생기지 않고 오류가 난다. 토픽은 관리자가 만든다 |
| 기본 파티션 · 복제 | 파티션 1, 복제 계수 1, min ISR 1 | |
| 기본 보관 | 7일, 세그먼트 1 GiB | |
| 접근 | bastion을 거친 SSH | |

### 클러스터와 환경

| 클러스터 | 환경 | 위치 |
|---|---|---|
| 개발 클러스터 | `dev` · `test` **같이 쓴다** | 개발 VPC — test DB·Connect와 같은 EC2 (`t3.medium`) |
| 운영 클러스터 | `prod` | 운영 VPC — Connect와 같은 EC2 (`t3.medium`) |

- 두 클러스터는 구성이 같다 — 이 문서의 설정은 둘 다에 적용된다.
- **CA도 클러스터마다 따로다** — 운영 서버에는 운영 CA 인증서, 개발·테스트 서버에는 개발 CA 인증서를 넣는다. 바꿔 넣으면 TLS 검증에서 실패한다.

### 노드가 하나라서

- **노드가 내려가면 Kafka 전체가 멈춘다** — 넘겨받을 복제본이 없다.
  - 요청 이벤트는 잃지 않는다 — 아웃박스 행이 DB에 있고 Debezium이 슬롯 위치부터 이어서 보낸다. 대신 그동안 Postgres에 WAL이 쌓인다([cdc.md](cdc.md) 「복제 슬롯」).
  - 완료 이벤트는 AI 서버가 보내지 못한다 — 다시 보내는 책임은 AI 서버에 있다.
- **디스크를 잃으면 보관 중인 메시지를 잃는다.** 요청 이벤트는 아웃박스 행이 남지만 다시 보내는 절차는 없다.
- 노드를 늘릴 때는 복제 계수·min ISR·내부 토픽 복제 계수를 함께 올리고, 새 노드마다 자기 주소로 서버 인증서를 발급한다(같은 CA로 서명하면 클라이언트는 그대로).

## 이름 — 환경 접두어 `{env}`

dev·test가 클러스터 하나를 같이 써서, **토픽·컨슈머 그룹·계정 이름 앞에 환경을 붙여** 서로의 이벤트를 아예 보지 못하게 한다. 다른 환경 계정으로 접근하면 권한 오류로 끝난다 — 설정을 잘못 넣은 서버는 접속 단계에서 막힌다.

- `{env}`는 `dev` · `test` · `prod` 중 하나다. 운영은 클러스터가 달라도 같은 규칙을 쓴다.
- **이벤트 안에 환경 필드를 넣어 걸러 내지 않는다** — 토픽이 다르면 애초에 받지 않는다.
- 구분자는 `.`과 `-`만 쓴다 — Kafka는 메트릭 이름에서 `.`과 `_`를 같게 본다.
- running-service는 `KAFKA_ENV` 하나로 모든 이름을 만든다(`KafkaNamingProperties`).

| 대상 | 이름 |
|---|---|
| 요청 토픽 | `{env}.record-card.music.requested` |
| 완료 토픽 | `{env}.record-card.music.generated` |
| running-service 계정 · 컨슈머 그룹 | `{env}.running-service` · `{env}.running-service.record-card-music` |
| AI 서버 계정 · 컨슈머 그룹 | `{env}.runiverse-ai` · `{env}.runiverse-ai.record-card-music` |
| Debezium 계정 | `{env}.debezium` (test·prod — dev는 커넥터가 없다) |
| Connect 그룹 · 내부 토픽 | `{env}.debezium-connect` · `{env}.debezium-connect.configs` · `.offsets` · `.status` |
| Debezium heartbeat 토픽 | `{env}.debezium-heartbeat.outbox` |

## 토픽

| 토픽 | 파티션 | 복제 | 보관 | 정리 방식 |
|---|---|---|---|---|
| `{env}.record-card.music.requested` | 1 | 1 | 7일 | `delete` |
| `{env}.record-card.music.generated` | 1 | 1 | 7일 | `delete` |
| `{env}.debezium-connect.configs` · `.offsets` · `.status` | 1 | 1 | 키마다 최신 값 | **`compact`** |
| `{env}.debezium-heartbeat.outbox` | 1 | 1 | **1일** | `delete` |

- **이벤트 토픽은 `delete`여야 한다** — 같은 카드의 이벤트가 같은 key로 여러 번 나간다(9-2 작성 뒤 9-3 수정). `compact`면 앞 이벤트가 지워진다.
- **Connect 내부 토픽은 `compact`여야 한다** — 커넥터 설정과 "WAL을 어디까지 보냈는지"를 키마다 최신 값으로 남긴다. `delete`면 7일 뒤 지워져 커넥터가 처음부터 다시 시작한다. configs는 파티션이 반드시 1개다.
- **heartbeat 토픽은 아무도 읽지 않는다** — Debezium이 위치를 기록하는 계기다. "Debezium이 마지막으로 살아 있던 때"를 볼 때만 쓴다.
- **파티션 수는 처음에 정하고 바꾸지 않는다** — 파티션은 `hash(key) % 파티션 수`라, 늘리면 같은 카드가 다른 파티션으로 가 순서가 깨진다(줄이는 것은 Kafka가 허락하지 않는다).

### 지금 설정 때문에 지켜야 할 것

- **완료 이벤트는 running-service 한 대만 처리한다** — 파티션이 1개라 ALB 뒤 인스턴스가 여럿이어도 한 대만 읽고 나머지는 대기한다. 처리 결과는 DB에만 쓴다(architecture.md 「다중 인스턴스」).
- **배포·스케일링 때 같은 완료 이벤트가 다시 온다** — 리밸런스 중 커밋하지 못한 이벤트를 다른 인스턴스가 받는다. inbox가 거른다([outbox-inbox.md](outbox-inbox.md)).
- **받는 쪽이 7일 넘게 멈추면 이벤트를 잃을 수 있다.**
- **running-service는 `auto.offset.reset=earliest`다** — 처음 붙는 그룹과, 7일 넘게 비어 커밋한 오프셋이 지워진 그룹은 남아 있는 이벤트를 처음부터 받는다. 이미 처리한 것은 inbox가 거른다.
- **실제 토픽에 접속 검증 메시지를 남기지 않는다** — 받는 쪽이 읽지 못하는 메시지가 되고, 기다려서 없애려면 세그먼트 규칙 때문에 약 14일이 걸린다. 보냈다면 확인 뒤 `kafka-delete-records`로 지운다.

## 접속 보안 — TLS + SASL + ACL

### 리스너

| 리스너 | 열린 곳 | 프로토콜 | 인증 | 쓰는 쪽 |
|---|---|---|---|---|
| `SASL_SSL` | 브로커 주소 :9092 | TLS 1.3 / 1.2 | SCRAM-SHA-512 | 모든 클라이언트, 브로커끼리 통신 |
| `CONTROLLER` | 127.0.0.1:9093 | SASL_PLAINTEXT | PLAIN | 같은 EC2 안 컨트롤러만 |

- 클라이언트에게 알려 주는 주소(`advertised.listeners`)는 `SASL_SSL` 하나다.
- 컨트롤러 리스너는 루프백이라 TLS 없이 PLAIN을 쓴다. 노드를 늘려 EC2 밖과 통신하게 되면 TLS를 붙인다.

### TLS

자체 CA(10년)가 서버 인증서(1년)에 서명했다. 클라이언트는 CA 인증서로 서버 인증서를 검증한다.

| 가진 쪽 | 파일 | 쓰임 |
|---|---|---|
| 브로커 | 키스토어 (PKCS12) | 서버 인증서 + 개인키 |
| Kafka EC2 | 트러스트 스토어 (PKCS12) | 같은 EC2 안에서 클라이언트로 붙는 쪽(브로커끼리, 관리자 CLI, Connect) |
| running-service · AI 서버 | **CA 인증서 (PEM)** | 서버 인증서 검증. 비밀번호가 필요 없다 |

- **서버만 인증서를 낸다**(`ssl.client.auth=none`) — 클라이언트가 누구인지는 SASL로 증명한다.
- **호스트 검증을 켠다**(`ssl.endpoint.identification.algorithm=https`) — 서버 인증서 SAN에는 **브로커의 VPC 안 주소 하나**만 있고, 이 주소는 리스너가 바인딩한 주소·`advertised.listeners`와 같다. 클라이언트는 그 주소로만 붙는다(DNS 이름·Elastic IP로 붙으면 실패한다). 그래서 **같은 VPC 안(또는 피어링)에서만 붙을 수 있다.** 인스턴스를 새로 만들어 주소가 바뀌면 서버 인증서를 다시 발급하고 접속 주소도 바꾼다.

**주의**

- **서버 인증서는 1년마다 만료된다** — 만료되면 모든 클라이언트가 한꺼번에 끊긴다. 같은 CA로 다시 발급해 키스토어만 바꾸면 클라이언트는 그대로다.
- **CA 개인키가 있으면 누구나 신뢰받는 서버 인증서를 만든다** — 갱신할 때만 필요하다.
- **CA 인증서는 클라이언트가 도는 곳마다 있어야 한다** — running-service는 Auto Scaling·Docker로 배포되므로 새 인스턴스·컨테이너에서도 `KAFKA_SSL_CA_LOCATION` 경로에 파일이 있어야 한다. CA 인증서는 공개 정보라 시크릿이 아니다.

### SASL

- 메커니즘은 **SCRAM-SHA-512** 하나다. 계정은 KRaft 메타데이터에 있어, 더하거나 비밀번호를 바꿔도 브로커를 재시작하지 않는다.
- 관리자 `kafka-admin`(super user)은 클러스터를 만들 때 등록했고, 서비스 계정은 관리자가 추가한다.
- **`kafka-admin` 비밀번호는 두 군데에 따로 있다** — SCRAM(메타데이터)과 컨트롤러 리스너의 PLAIN(서버 설정 파일). 한쪽만 바꾸면 나머지 쪽 통신이 끊긴다.
- 비밀번호가 든 설정 파일은 Kafka EC2 안에서 `root:kafka 640`, 다른 서버에서 `600`으로 둔다.
- 서비스 계정마다 비밀번호를 다르게 둔다(같은 서비스라도 환경마다, DB 계정과도 다르게).

### ACL

- `StandardAuthorizer`, **허용하지 않은 것은 모두 거부**한다.
- **서비스 계정에는 `CREATE`를 주지 않는다** — 토픽은 관리자만 만든다.
- **이름 하나씩(LITERAL) 준다** — 접두어로 묶으면 나중에 같은 접두어로 생긴 토픽에 권한이 따라간다.
- **컨슈머 그룹은 만들지 않고 `READ`만 준다** — 컨슈머가 처음 붙을 때 생긴다.

| 계정 | 리소스 | 권한 |
|---|---|---|
| `{env}.running-service` | 토픽 `requested` | **없음** — 요청은 Debezium이 보낸다 ⚠️ |
| `{env}.running-service` | 토픽 `generated` | `READ` `DESCRIBE` |
| `{env}.running-service` | 그룹 `{env}.running-service.record-card-music` | `READ` |
| `{env}.runiverse-ai` | 토픽 `requested` | `READ` `DESCRIBE` |
| `{env}.runiverse-ai` | 토픽 `generated` | `WRITE` `DESCRIBE` |
| `{env}.runiverse-ai` | 그룹 `{env}.runiverse-ai.record-card-music` | `READ` |
| `{env}.debezium` | 토픽 `requested` | `WRITE` `DESCRIBE` |
| `{env}.debezium` | Connect 내부 토픽 3개 | `READ` `WRITE` `DESCRIBE` `DESCRIBE_CONFIGS` |
| `{env}.debezium` | 그룹 `{env}.debezium-connect` | `READ` |
| `{env}.debezium` | 토픽 `{env}.debezium-heartbeat.outbox` | `WRITE` `DESCRIBE` |

- ⚠️ **`test`·`dev`의 running-service에는 요청 토픽 `WRITE` `DESCRIBE`가 아직 남아 있다**(앱이 직접 발행하던 설계의 권한, `prod`는 뺐다). 남아 있는 동안은 실수로 넣은 직접 발행 코드가 권한 오류 없이 동작한다 — 리뷰에서 running-service에 Kafka 프로듀서가 들어오지 않는지 본다(outbox-inbox.md 「앱은 Kafka로 직접 보내지 않는다」).

## 보내는 쪽은 중복 방지를 켠다

- **Kafka에 쓰는 모든 프로듀서는 `enable.idempotence=true`로 보낸다** — 재전송 중복을 줄이고 재전송 때도 파티션 안 순서를 지킨다. 토픽 `WRITE`만 있으면 된다.
- 브로커가 강제하지 않는다 — 클라이언트마다 켠다. Debezium은 Connect 워커 설정(`producer.enable.idempotence=true`)에 명시한다. **AI 서버의 Python 클라이언트는 라이브러리에 따라 기본이 꺼짐**(예: `confluent-kafka`)이라 명시해야 한다.
- 켜도 받는 쪽 중복은 남는다 — inbox는 그대로 둔다.

## running-service 접속 설정

running-service는 완료 토픽만 받는다(컨슈머 하나, 프로듀서 없음 — 요청은 Debezium이 보낸다). 고정 값은 `application.properties`에 있다 — `SASL_SSL`, `SCRAM-SHA-512`, CA는 PEM(`ssl.truststore.type=PEM`), 호스트 검증 `https`, 수동 커밋(레코드마다), `auto.offset.reset=earliest`, key·value 모두 문자열. 환경마다 다른 값만 변수로 받는다.

| 변수 | 예 | 설명 |
|---|---|---|
| `KAFKA_ENV` | `test` | `{env}` 값. 아웃박스 `topic`과 토픽·그룹 이름을 이 값 하나로 만든다. **기본값이 없고 `dev`·`test`·`prod`가 아니면 기동하지 않는다.** Parameter Store `/runiverse/test/KAFKA_ENV`(`test`) · `/runiverse/deploy/KAFKA_ENV`(`prod`). 로컬은 **붙는 DB의 환경**을 따른다([cdc.md](cdc.md)) |
| `KAFKA_CONSUMER_ENABLED` | `true` | 완료 구독을 켠다. **기본값이 없어 없으면 기동하지 않는다.** `false`면 리스너·오류 처리기를 만들지 않아 아래 값을 보지 않는다(로컬·E2E). `true`면 아래 네 값이 모두 있고 CA 파일을 읽을 수 있어야 기동한다 — 빠진 변수 이름만 알리고 값은 싣지 않는다. Parameter Store test·deploy 모두 `true` |
| `KAFKA_BOOTSTRAP_SERVERS` | `<브로커 VPC 주소>:9092` | 인증서 SAN과 같은 주소 |
| `KAFKA_SASL_USERNAME` | `test.running-service` | `{env}.running-service` |
| `KAFKA_SASL_PASSWORD` | | 시크릿(SecureString) — 로그·출력에 남기지 않는다 |
| `KAFKA_SSL_CA_LOCATION` | `/app/config/kafka-ca.crt` | 그 환경 클러스터의 CA 인증서(PEM) 절대 경로. 배포 서버는 아래 「CA 인증서 배포」가 이 경로에 마운트한다 |

- **토픽이 없으면 기동하지 않는다**(`missing-topics-fatal=true`) — 토픽 이름(`KAFKA_ENV`)이 틀리면 배포가 실패로 드러난다.
- **배포 순서** — 새 변수는 그 변수를 읽는 버전을 배포하기 전에 Parameter Store에 넣는다(기본값이 없다). 옛 버전은 모르는 변수를 무시한다.

### CA 인증서 배포

| 무엇 | 값 |
|---|---|
| 원본 | Parameter Store `/runiverse/files/{test\|deploy}/kafka-ca.crt` (SecureString) |
| 서버에 두는 곳 | `/opt/runiverse/kafka-ca.crt` — `root`, `644` |
| 컨테이너 | `/app/config/kafka-ca.crt`에 읽기 전용 마운트 |
| test | 배포 워크플로(`backend-ci-cd.yml`, SSM 명령)가 받는다 — 새 컨테이너와 롤백 컨테이너 모두 마운트 |
| prod | Auto Scaling 시작 템플릿의 사용자 데이터가 인스턴스를 띄울 때 받는다(버전 8부터). 그룹은 템플릿 `Latest`를 쓴다 |

- **앱 변수 경로(`/runiverse/{test|deploy}/`) 밖에 둔다** — 배포 스크립트가 그 경로를 통째로 env 파일로 만들고, 여러 줄 값(PEM)은 거부한다.
- **값을 출력하지 않는다** — SSM 명령의 출력은 Actions 로그에, 사용자 데이터의 출력은 부트스트랩 로그(`/var/log/runiverse-bootstrap.log`)·콘솔 로그에 남는다. 파일로만 쓰고 `BEGIN CERTIFICATE`가 있는지만 확인한다.
- **`644`다** — 컨테이너 사용자가 읽어야 한다. CA 인증서는 공개 정보라 읽혀도 되고, 막을 것은 **바꿔치기**다 — 지킬 것은 그 파라미터의 쓰기 권한(`ssm:PutParameter`)이다. 비밀번호가 든 `.env`는 `600`이다.
- **인증서를 바꾸면** — 값을 고친 뒤 test는 다시 배포, prod는 인스턴스 새로 고침(종료 전 시작, 최소 정상 100%)으로 새 인스턴스에 반영한다.
- **시작 템플릿을 고칠 때** — 원본 버전을 지금 쓰는 버전으로 고르고, 만든 뒤 바로 앞 버전과 **사용자 데이터 말고는 같은지** CLI로 비교한다(`describe-launch-template-versions`에서 `UserData`를 뺀 비교) — 콘솔 화면만으로는 원본 값이 모두 옮겨졌는지 알 수 없다. 사용자 데이터만 바꿀 때는 `create-launch-template-version --source-version`으로 만들면 나머지가 그대로 복사된다.
- AI 서버의 CA 인증서는 AI 쪽에서 넣는다.

### 로컬 앱으로 붙기

브로커는 VPC 안 주소를 알려 주고(`advertised.listeners`) 서버 인증서 SAN도 그 주소 하나라, 로컬에서 다른 주소로 붙으면 재접속·호스트 검증에서 실패한다. **로컬에서도 같은 주소로 붙게 만든다.**

- 로컬 루프백에 브로커의 VPC 주소를 별칭으로 붙이고, bastion을 거치는 SSH 터널을 **그 주소:9092**에 연다 — 앱은 `KAFKA_BOOTSTRAP_SERVERS`에 VPC 주소를 그대로 쓴다.
- 개발 클러스터 계정(`dev.running-service`)과 개발 CA로 붙는다. CA 파일은 `infra/kafka/`에 두고 커밋하지 않는다(`.gitignore` `infra/kafka/*.crt`).
- 확인이 끝나면 터널을 닫고 별칭을 지우고, `KAFKA_CONSUMER_ENABLED=false`로 되돌린다.

## CDC를 붙일 때 Kafka에 필요한 것

[cdc.md](cdc.md) 「새 환경에 CDC를 붙이는 순서」의 Kafka 단계다. test·prod는 갖춰져 있다.

- 계정 `{env}.debezium`
- 요청 토픽 권한 — `{env}.debezium`에 `WRITE` `DESCRIBE`를 주고, `{env}.running-service`에서는 뺀다(주는 쪽을 먼저)
- Connect 내부 토픽 3개 — `compact`, 복제 계수 1, configs는 파티션 1, 이름은 `{env}.`로 시작 — 와 그 권한, Connect 그룹 권한
- heartbeat 토픽 — 보관 1일 — 과 `WRITE` `DESCRIBE`
- Connect가 도는 곳에 그 클러스터의 CA(트러스트 스토어)

## 정하지 않은 것

1. **파티션 수** — 지금 1이다. 완료 구독 처리량이 모자라면 실제 이벤트가 쌓이기 전에 다시 정한다.
2. **인증서 관리** — 클러스터마다 서버 인증서 만료일, 만료 전 알림, CA 개인키 보관 위치(브로커 EC2 밖), 갱신 절차.
3. ~~CA 인증서 배포~~ — **정했다**: running-service는 Parameter Store → test 배포 워크플로·prod 시작 템플릿 사용자 데이터 → 읽기 전용 마운트(「CA 인증서 배포」). AI 서버는 AI 쪽에서 넣는다.
4. **모니터링** — 브로커 상태·디스크·컨슈머 지연([metrics-convention.md](../metrics-convention.md)와 맞춘다).
5. **검증용 토픽** — 접속을 확인할 때 쓸 토픽과 권한.
6. **`test`·`dev` running-service의 요청 토픽 권한 제거** — 위 ACL ⚠️.
