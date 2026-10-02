# 레이어 패턴

문서가 정책 기준이고 기존 파일은 구현 형태의 예시다. 둘 중 어느 쪽이든 낡았을 수 있으니, 어긋나면 한쪽을 그대로 따르지 말고 차이를 알린다.

경로는 전부 `running-service/src/main/java/com/runiverse/running_service/` 기준.

## 도메인 VO — `domain/user/vo/`

열어볼 것: `AvgPace`, `Nickname`, `domain/common/vo/UserId`

- `record` 컴팩트 생성자에서 파라미터에 재대입해 정규화한 값을 필드에 넣는다.
- 접근자는 의미가 드러나게 짓고 무조건 `value()`로 만들지 않는다.
- 규칙 하나에 예외 클래스 하나. 예외는 `domain/<도메인>[/<하위>]/exception/`, 코드·메시지는 `domain/common/exception/`의 도메인별 `*ErrorCode` enum.

## 도메인 애그리거트 — `domain/user/`

열어볼 것: `User`, `UserOnboarding`

- 애그리거트는 도메인 패키지 바로 아래에 둔다. 도메인이 크면 하위 패키지로 나눈다(`domain/running/room/`).
- 생성자는 원시 값을 VO로 감싼다. 검증은 VO에 위임하고 애그리거트는 내부 상태의 불변식만 본다.
- nullable한 내부 상태는 `Optional`로 노출한다.
- 값 성격의 내부 엔티티는 수정 시 새 인스턴스를 반환한다(`UserOnboarding.change(...)`).
- 애그리거트 내부 엔티티(`UserOnboarding`·`OauthUser`)는 동등성을 식별자 기준으로 재정의한다. 루트(`User`)는 재정의하지 않는다.

## 유스케이스 — `application/auth/command/signup/`, `login/`

열어볼 것: `SignUpHandler`, `LoginHandler`

- Command·Result는 원시 타입·UUID를 사용하고, record VO는 컨트롤러까지 노출하지 않는다. 도메인 enum은 Result·Response에서 그대로 쓰는 선례가 있다(`GetMyRunningRecordsResult`·`RoomInfoResponse`).
- `@Transactional`은 **스프링 것**(`org.springframework.transaction.annotation`)을 쓴다.
- Handler는 조립과 순서만 제어한다. 값 규칙은 도메인, 저장·조회는 포트가 맡는다.

## 조회 유스케이스 — `application/running/query/record/`

열어볼 것: `GetMyRunningRecordsHandler`

- `query/<기능>/`에 Query·Handler·Result를 둔다. Handler에 `@Transactional(readOnly = true)`를 건다.

## 로그·메트릭

- 정책은 `logging-convention.md`·`metrics-convention.md`를 따른다. 핸들러 메트릭 구현 예시는 `OauthLoginHandler` → `RecordAuthMetricPort` → `AuthMetricAdapter`.

## 포트 — `application/auth/port/`

열어볼 것: `port/in/SignUpUsecase`, `port/out/CheckEmailDuplicatePort`

- 분리 기준은 `architecture.md`의 `port/out` 규칙을 따른다. 이름은 동작·역할을 드러내며 특정 접두사로 제한하지 않는다.
- 파라미터·반환에 도메인 타입(`UserId`, `User`)을 써도 된다. 포트는 application 소유라 domain 의존은 방향이 맞다.
- `port/out`에는 아웃바운드 인터페이스와 그 전용 입출력 모델만 둔다(예: `OauthProfile`). 여러 레이어가 함께 쓰는 DTO는 기능 패키지에 둔다.

## JPA 엔티티 — `infrastructure/persistence/user/`

열어볼 것: `UserOnboardingJpaEntity`(제약 총집합), `UserJpaEntity`

- 컬럼 제약(`nullable`·`length`·`precision`/`scale`)과 `@Check`·`@UniqueConstraint`를 `erd.md` 표 그대로 옮긴다. 제약 이름은 `uk_`·`ck_`·`fk_`로 시작하고 나머지는 같은 도메인의 기존 엔티티에 맞춘다.
- setter를 두지 않는다. `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static `create(...)`.
- 감사 컬럼은 직접 선언하지 않고 `infrastructure/persistence/common/`의 베이스를 상속한다 — `created_at`만 있으면 `BaseCreatedAtEntity`, `updated_at`도 있으면 `BaseTimeEntity`(`erd.md` §0). JPA Auditing은 쓰지 않는다.
- FK 제약은 걸되 값을 직접 관리할 때는 `insertable = false, updatable = false` 연관을 별도로 둔다.
- CASCADE와 논리 참조는 `erd.md` §0의 `user_id` FK 정책을 따른다. 논리 참조 테이블에는 FK 제약을 걸지 않는다.

## 영속성 어댑터 — `infrastructure/persistence/user/UserPersistenceAdapter`

- JPQL 텍스트 블록에는 테이블명이 아닌 엔티티명(`UserJpaEntity`)을 쓴다.
- 단건 조회는 `getResultStream().findFirst()`로 받아 `Optional`로 돌려준다. `getSingleResult()`는 없으면 예외를 던진다.
- 도메인과 DB 표현이 다르면 변환을 어댑터에서 흡수한다.

## Redis 어댑터 — `infrastructure/redis/`

- 키는 `RedisKey` enum으로 조립한다. 문자열을 직접 이어 붙이지 않는다. 새 종류가 필요하면 enum에 prefix를 추가한다.
- TTL 같은 운영값은 `@ConfigurationProperties`에서 읽고 하드코딩하지 않는다.

## 컨트롤러 — `presentation/auth/controller/AuthController`

- 필드는 `*Usecase` 인터페이스 타입으로 받고 Handler 구현체를 직접 주입하지 않는다.
- `try/catch`를 쓰지 않는다. 예외는 `GlobalExceptionHandler`가 일괄 변환한다.
- 인증은 `@AuthenticationPrincipal Jwt jwt` → `UUID.fromString(jwt.getSubject())`.
- 본문 없는 응답은 `ResponseEntity.noContent().build()`.

## 요청·응답 DTO — `presentation/user/request/OnboardingRequest`

- 필수 숫자는 `Integer`와 `@NotNull`로 받는다. `int`는 누락을 0으로 바꾼다.
- enum은 `String`과 `@Pattern`으로 검증한다. enum 타입은 Jackson의 `MALFORMED_REQUEST_BODY`가 먼저 발생해 명세 메시지를 제어할 수 없다.
- 물리량 필드명에는 `api-convention.md` "물리량 단위"의 접미사를 붙인다. 메시지는 `api-spec.md` 문구 그대로 쓴다.
- 날짜 쿼리 파라미터는 `RunningRecordsRequest`를 참고한다. `LocalDate` 대신 `String`과 `@AssertTrue`를 쓰는 이유는 파일 주석에 있다.
