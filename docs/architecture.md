# Architecture

클린 아키텍처 + DDD. 패키지 루트는 `com.runiverse.running_service`다.

## 의존 방향

```
presentation ──▶ [port/in] application [port/out] ◀── infrastructure
                                │
                                ▼
                              domain
```

- `domain`은 어떤 레이어도 import하지 않는다 — Spring·JPA에도 의존하지 않는다.
- `application`이 포트를 소유하고, `presentation`은 `port/in`을 호출하며 `infrastructure`는 `port/out`을 구현한다.
- 그림은 코드 의존 방향이다.

## 패키지 구조

```
domain/
  <domain>/             aggregate · vo · exception
  common/exception/     BusinessException · ErrorCode (도메인용)

application/
  <domain>/command/     상태를 바꾸는 유스케이스 — 기능별 Command · Handler · Result(선택)
  <domain>/query/       조회 유스케이스 — 기능별 Query · Handler · Result
  <domain>/port/in/     *Usecase — Handler가 구현
  <domain>/port/out/    아웃바운드 인터페이스 · 전용 입출력 모델
  <domain>/exception/   외부 확인이 필요한 실패
  common/exception/     BusinessException · ErrorCode (유스케이스용)
  common/port/out/      도메인에 매이지 않는 공통 아웃바운드 포트

infrastructure/         기술 단위 — persistence · redis · security · oauth · mail · storage · identifier · config
presentation/
  <domain>/             controller · request · response
  common/exception/     GlobalExceptionHandler · *ErrorCode · ErrorExposurePolicy
  common/response/      ErrorResponse
  common/security/      JwtAuthenticationEntryPoint · JwtAccessDeniedHandler
```

## 요청 흐름

```
AuthController ──▶ SignUpUsecase ──▶ SignUpHandler ──▶ SaveUserPort ──▶ UserPersistenceAdapter
                     (port/in)       @Transactional      (port/out)        (infrastructure)
                                           │
                                      new User(…)  ← domain
```

경계마다 데이터 표현이 `SignUpRequest` → `SignUpCommand` → `User` → `UserJpaEntity`로 바뀐다. 변환은 경계를 넘기는 쪽이 한다: 컨트롤러는 `Command`를 만들고, 어댑터는 `toDomain()`으로 되돌린다.

## 레이어별 규칙

- **domain/**: VO는 생성 시점에 검증하고 도메인 예외를 던진다.
- **application/**: 상태 변경은 `command/`, 조회는 `query/`에 두고 후자에 `@Transactional(readOnly = true)`를 건다. `port/in`은 둘을 구분하지 않는다. DB 트랜잭션은 application에서 관리한다. 보통 Handler가 경계지만 필요하면 내부 컴포넌트가 맡고, Redis 전용처럼 DB를 쓰지 않으면 경계가 없어도 된다.
- **port/out**: 작고 응집된 인터페이스로 나누고 Handler에는 필요한 포트만 주입한다. 사용 유스케이스나 변경 이유가 다르면 포트를 분리한다.
- **infrastructure/**: 도메인 ↔ JPA 변환을 맡는다. application 포트의 기본 기술 구현체는 `*Adapter`로 명명한다. 외부 제공자와 직접 통신하면 `*Client`, 여러 Client를 선택해 application 포트를 구현하면 `*Router`로 명명한다. 인스턴스 로컬 상태를 보관하며 그 조회 포트를 겸하는 컴포넌트는 `*Registry`로 명명할 수 있다. 같은 애그리거트·저장 기술의 포트는 어댑터 하나가 함께 구현할 수 있다.
- **presentation/**: 컨트롤러 처리 중 발생한 예외는 `GlobalExceptionHandler`, 인증 진입 실패는 `AuthenticationEntryPoint`에서 변환한다.

## DDD 규칙

- **애그리거트 루트**: 루트만 외부에 노출한다. `User`가 `OauthUser`·`UserOnboarding`을 내부에 들고, 포트는 루트 단위로 저장·조회한다.
- **애그리거트 행위**: 애그리거트가 스스로 불변식을 지킨다. 상태는 `linkOauth`·`completeOnboarding`처럼 의도가 드러나는 메서드로만 바꾸고, 위반 시 도메인 예외를 던진다. 생성 방식이 여럿이면 정적 팩토리(`User.registerWithOauth`)로 구분한다.
- **VO**: 식별자·이메일·닉네임처럼 의미 있는 값은 원시 타입 대신 `record`로 만들어 불변·값 동등성을 강제하고 생성자에서 검증한다. 고정된 값 집합은 `enum`(`Gender`·`Provider`)으로 둔다. Request DTO의 Bean Validation과 VO 검증은 목적(400 노출 / 불변식)이 다르므로 합치지 않고, VO를 정본으로 함께 고친다.
- **애그리거트 간 참조**: 객체는 ID로 참조한다(*Reference by Identity*). DB FK 여부는 수명주기와 삭제 정책에 따라 [erd.md](erd.md)를 따르며, 독립적인 논리 참조는 앱에서 무결성을 관리한다. 애그리거트 내부는 객체 참조와 FK를 사용한다.
- **예외 구분**: VO 검증·애그리거트 불변식 위반은 `domain/`, 외부 확인이 필요한 실패(중복·조회·인증·연동)는 `application/`에 둔다. 두 레이어에 동명 `BusinessException`·`ErrorCode`가 있으니 import 시 확인한다.

## 구현 스타일 기준

기존 구현을 참고할 때는 `application/auth/command/signup`·`login` 패키지가 기준이다.

현재의 리팩터링 예외는 아래 범위에만 적용한다. 새 의존을 추가하거나 기존 예외 범위를 넓히는 근거로 삼지 않는다.

- **security 직접 참조**: 현재 허용하는 import는 `SecurityConfig` → `JwtAuthenticationEntryPoint`·`JwtAccessDeniedHandler`, `JwtAuthenticationEntryPoint` → `BlockedTokenValidator`·`ExpiredTokenValidator`뿐이다.
- **`UserOnboarding` 별도 영속화**: `ExistsOnboardingPort`의 별도 존재 확인, `CompleteOnboardingHandler` → `SaveOnboardingPort.saveOnboarding(UserOnboarding)` → `UserPersistenceAdapter`의 별도 저장, `UserPersistenceAdapter.toDomain(UserJpaEntity)`가 `UserOnboarding`을 복원하지 않는 현재 흐름만 허용한다.
