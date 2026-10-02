# 에러 처리

## `BusinessException`이 두 개다

| | `domain.common.exception` | `application.common.exception` |
|---|---|---|
| 언제 | VO 생성·애그리거트 상태 전이 위반 | 유스케이스가 요청을 튕겨낼 때 |
| 위치 | `domain/<도메인>[/<하위>]/exception/` | `application/<도메인>/exception/` |
| 응답 | **항상 500** | `toStatus()` + 노출 정책 통과 시 그 상태(전용 핸들러가 있으면 그쪽, 예: `MatchCooldownException`) |

값만 보고 판단하면 도메인, 저장소·외부 상태가 필요하면 애플리케이션 예외다. 코드 enum도 양쪽에 도메인별로 있고 sealed `ErrorCode`와 `UserErrorCode`는 이름이 겹친다 — import 패키지를 확인한다.

## 도메인 예외가 500인 이유

`handleDomainException`은 항상 500을 반환한다. **400 검증은 Request DTO의 Bean Validation이 담당한다.** 값 규칙은 VO를 정본으로 DTO와 함께 바꾼다.

## 등록 3곳

1. **`application/common/exception/<도메인>ErrorCode`** — 해당 도메인 enum에 상수를 추가하고 코드 문자열·메시지를 `api-spec.md`와 글자 그대로 맞춘다. 새 도메인 enum을 만들면 sealed interface `ErrorCode`의 `permits`에도 추가한다.
2. **`GlobalExceptionHandler.toStatus()`** — enum마다 overload가 있고 `default` 없는 switch라 빠뜨리면 컴파일이 깨진다. 새 도메인 enum이면 overload와 dispatcher의 `case`를 추가한다.
3. **`ErrorExposurePolicy.EXPOSED_CODES`** — 400은 자동 노출이라 등록이 필요 없고, 그 외 상태 중 공개할 코드만 추가한다. 공개 대상인데 빠지면 **컴파일·테스트가 통과해도** 응답이 500으로 바뀐다(런타임 WARN 로그만 남는다).

## 노출 정책

`isExposed`는 상태가 400이거나 코드가 `EXPOSED_CODES`에 있을 때만 참이다. 거짓이면 `GlobalExceptionHandler`가 상태를 500으로, 본문을 `masked()`(`INTERNAL_SERVER_ERROR`)로 바꾼다.

## 일부러 감추는 경우

기존 코드가 `EXPOSED_CODES`에 없으면 추가하기 전에 `git log -S <코드명>`으로 의도적 비노출인지 확인한다. 의도된 비노출은 이유를 주석이나 결정 문서에 남긴다(`USER_NOT_FOUND`는 `toStatus`에서 500을 지정하고 주석을 단다).

WebSocket으로만 보내는 코드는 핸들러가 직접 보내므로 이 경로를 타지 않는다. 그래도 switch를 비울 수 없어 의미에 맞는 상태를 적어 둔다.

## 컨트롤러 앞단의 에러

`@Valid`·JSON 파싱 실패는 `CommonErrorCode`, 인증·인가 실패는 `SecurityErrorCode`와 `JwtAuthenticationEntryPoint`·`JwtAccessDeniedHandler`가 담당한다(모두 `presentation/common/` 아래). 응답 코드·메시지는 기존 상수명이 아닌 `api-spec.md`를 기준으로 대조한다.
