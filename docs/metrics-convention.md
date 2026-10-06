# Metrics Convention

메트릭 이름·태그·계측 종류·기록 위치 규칙 — 메트릭을 새로 만들거나 고칠 때 모두 이 규칙을 따른다.

## 수집 구조

- 앱은 Micrometer로 기록하고 `/actuator/prometheus`로 노출한다. Prometheus가 주기적으로 긁어 가고(pull), Grafana가 Prometheus를 조회한다.
- 앱은 누적값만 들고 있다. Prometheus가 읽어 가도 지워지지 않고, 앱이 재시작할 때만 0으로 돌아간다. 시각별 기록은 Prometheus에 쌓이고, 증가 속도(`rate()`)는 Grafana에서 계산한다.
- 모든 메트릭에 공통 태그 `application=running-service`를 붙인다.

## 로그와 메트릭의 역할

| | 로그 | 메트릭 |
|---|---|---|
| 답하는 질문 | 누가, 왜 실패했나 | 얼마나 많이, 얼마나 빨리 |
| 식별자 | `userId`·`roomId`로 한 건을 찾는다 | 식별자를 쓰지 않는다 — 합계와 분포만 본다 |

- 알림은 메트릭으로 울리고, 원인은 로그로 찾는다. 알림 → 같은 시간대의 ERROR 로그 → requestId 순서로 추적한다.

## 이름

소문자와 점(`.`)으로 쓰고 `runiverse.`로 시작한다. 두 갈래가 있다.

### 진입점 메트릭

```
runiverse.<프로토콜>.<대상>
```

- 요청·메시지가 들어오는 입구마다 하나씩 두고, 성공·실패를 모두 센다. 기능별 구분은 태그(`domain`·`uri`·`event`)로 한다.
- `<대상>`은 세는 것을 복수 명사로 쓴다 — 같은 프로토콜의 다른 메트릭(`runiverse.websocket.connections`)과 이름이 겹치지 않게 한다.

| 메트릭 | 세는 것 |
|---|---|
| `runiverse.http.requests` | 처리한 HTTP 요청 |
| `runiverse.websocket.messages` | 받은 WebSocket 메시지 |
| `runiverse.sse.events` | 보낸 SSE 이벤트 |

### 핸들러 메트릭

```
runiverse.<도메인>.<기능 폴더>.<동작>
```

- 진입점 메트릭의 태그 조합으로 구할 수 없는 것만 만든다 — URL 템플릿에 가려진 값, 응답에 드러나지 않는 업무 결과(매칭이 기존 방에 합류했는지 새 방을 열었는지), 핸들러 안 특정 구간의 소요 시간.
- `<도메인>`은 `application/` 아래 패키지, `<기능 폴더>`는 그 아래 `command/`·`query/`의 기능 패키지다.
- `<동작>`은 핸들러 이름의 동사를 소문자로 쓴다(`OpenMatchStreamHandler` → `open`). 동사가 기능 폴더 이름과 같으면 생략한다(`signup/SignUpHandler` → `runiverse.auth.signup`).
- 클래스 이름을 그대로 쓰지 않는다 — 클래스 이름이 바뀌면 새 시계열이 생겨 이전 기록·대시보드·알림이 끊긴다.

### 공통

- Prometheus로 나갈 때 `.`이 `_`로 바뀌고 종류별 접미사가 붙는다(`runiverse_http_requests_total`).
- 단위를 이름에 넣지 않는다 — Timer는 초, 나머지는 `baseUnit`으로 지정한다.
- 결과를 이름으로 가르지 않는다(`...requests.success` ✗) — 태그 `result`로 가른다.

## 태그

**값의 종류가 정해진 것만 태그로 쓴다.** 태그 값 조합마다 시계열이 하나씩 생긴다 — 사용자 수만큼 늘어나는 값을 넣으면 Prometheus 메모리가 버티지 못한다.

| 쓴다 | 쓰지 않는다 |
|---|---|
| `domain=auth\|user\|match\|running\|scheduling\|common` | `userId`, `roomId`, `requestId`, `scheduledJobId` |
| `result=success\|failure` | 이메일, 좌표, 예외 메시지 |
| `reason=<아래 목록>` | 요청 경로 원문 — 템플릿(`/users/{userId}/profile`)만 |

- **같은 이름의 메트릭은 태그 key 구성이 항상 같아야 한다** — Prometheus 레지스트리가 강제한다. 해당 없는 값도 비우지 않고 정해진 값(`none`·`unknown`)을 넣는다.
- 태그 key는 camelCase, 값은 소문자로 쓴다. `reason`만 코드 이름을 그대로 쓴다.
- 사용자 입력을 태그 값으로 그대로 쓰지 않는다 — 지원하지 않는 event·provider처럼 목록 밖의 값은 `unknown`으로 바꾼다.

### domain

- 요청·메시지를 처리한 코드의 기능 패키지 이름이다. 로그 도메인 태그와 같은 값 집합이다(`[인증]` ↔ `auth`).
- HTTP 요청은 요청에 매핑된 컨트롤러의 패키지다(`/users/me/status` → `user`, `/users/me/running-records` → `running`). 엔드포인트별로 보려면 `uri`·`method`로 거른다.
- 처리할 곳에 닿기 전에 끝난 요청(없는 경로, 보안 필터 거절)은 `common`이다.

### result · reason

| 상황 | result | reason |
|---|---|---|
| 성공 | `success` | `none` |
| 업무 실패 | `failure` | `ErrorCode` 이름(`EMAIL_ALREADY_EXISTS`) |
| 요청 형식·검증 실패 | `failure` | `INVALID_REQUEST`, `MALFORMED_REQUEST_BODY` |
| 없는 경로 | `failure` | `NOT_FOUND` |
| 인증·인가 거절 | `failure` | 해당 코드 이름 |
| 예상 못 한 서버 오류 | `failure` | `INTERNAL_SERVER_ERROR` |
| 실패했는데 원인이 남지 않음 | `failure` | `UNKNOWN` — 원인을 남기지 않는 예외 경로가 생겼다는 신호다. 보이면 코드를 고친다 |

- HTTP는 응답 상태가 4xx·5xx면 `failure`다.
- 응답에서 500으로 숨기는 코드(`USER_NOT_FOUND`)도 `reason`에는 실제 코드를 쓴다 — 운영자는 진짜 원인을 봐야 한다.
- 원인을 코드보다 잘게 나누지 않는다 — 더 자세한 원인은 로그에 있다.

### 프로토콜별 태그

| 메트릭 | 태그 |
|---|---|
| `runiverse.http.requests` | `domain`, `method`, `uri`, `result`, `reason` |
| `runiverse.websocket.messages` | `domain`, `event`, `result`, `reason` |
| `runiverse.sse.events` | `domain`, `event`, `result`, `reason` |

- `uri`는 경로 템플릿이다. 매칭된 템플릿이 없으면(없는 경로, 보안 필터 거절) `UNKNOWN`이다.

## 계측 종류

| 종류 | 쓰는 곳 | 예 |
|---|---|---|
| Counter | 일어난 횟수 | 요청·메시지 수, 외부 API 실패 |
| Timer | 걸린 시간 + 횟수 | 외부 API 호출, 러닝 종료 처리 |
| Gauge | 지금 이 순간의 값 | WebSocket·SSE 연결 수 |
| DistributionSummary | 시간이 아닌 값의 분포 | 러닝 거리 |

- 횟수와 시간을 함께 보려면 Counter를 따로 두지 않고 Timer 하나로 쓴다 — Timer가 횟수도 센다.

## 기록 위치

- **진입점 메트릭:** 레이어를 import하지 않는 공통 장치는 `observability/metrics/`에 둔다. `reason`은 예외를 처리한 presentation(중앙 예외 핸들러, 인증 진입점)이 요청 속성에 남기고, `observability`가 그 값을 읽어 센다 — 속성 key 상수는 `observability`가 소유한다.
- **핸들러 메트릭:** application은 Micrometer를 import하지 않는다. `port/out`의 기록 포트를 호출하고, infrastructure가 `MeterRegistry`로 구현한다.
- **외부 시스템 호출 시간·실패:** infrastructure 어댑터가 직접 기록한다.

## 기본 제공과 직접 만드는 것

| 기본 제공 | 내용 |
|---|---|
| `http.server.requests` | 엔드포인트별 **응답 시간**·상태 코드 |
| `jvm.*`, `process.*` | 메모리·GC·스레드·CPU |
| `hikaricp.*` | DB 커넥션 풀 |
| `lettuce.*` | Redis 명령 |
| `logback.events` | 레벨별 로그 수 — ERROR 증가 알림에 쓴다 |

- 기본 제공으로 나오는 것은 직접 만들지 않는다. 예외: `runiverse.http.requests`는 `http.server.requests`와 요청 수가 겹치지만 `domain`·`reason`을 붙이려고 따로 센다. 응답 시간은 `http.server.requests`로 본다.

직접 만드는 것은 이 표에 먼저 추가하고 나서 만든다.

| 이름 | 종류 | 기록 위치 | 상태 |
|---|---|---|---|
| `runiverse.http.requests` | Counter | `observability/metrics/` | 사용 중 |
| `runiverse.auth.oauthlogin` | Counter | `OauthLoginHandler` → `AuthMetricAdapter` | 사용 중 — `provider=kakao\|google\|unknown`, `result`, `reason` |
| `runiverse.websocket.messages` | Counter | 러닝 WebSocket 핸들러 | 예정 |
| `runiverse.sse.events` | Counter | 매칭 스트림 연결 | 예정 |

## 노출

- `management.endpoints.web.exposure.include=health,prometheus` — 나머지 actuator 엔드포인트는 열지 않는다.
- `/actuator/prometheus`는 인증 없이 긁히지만 외부에서 닿으면 안 된다 — 네트워크에서 막는다(로드밸런서에 노출하지 않거나 별도 관리 포트).
- 응답 시간 백분위(p95·p99)는 Grafana에서 계산하도록 히스토그램을 켠다. 앱에서 백분위를 직접 계산하지 않는다 — 인스턴스 여럿의 값을 합칠 수 없다.
