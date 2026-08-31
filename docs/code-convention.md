# Code Convention

## 기본 원칙

- 포맷팅은 루트 `.editorconfig`를 기준으로 한다.
- `.editorconfig`와 프로젝트 문서에 없는 Java 규칙은 Google Java Style을 따른다.
- 커밋·브랜치·PR 규칙은 [git-convention.md](git-convention.md)를 따른다.
- API 표면(엔드포인트·DTO 필드) 규칙은 [api-convention.md](api-convention.md)를 따른다.

## 네이밍

- 클래스·인터페이스·enum·record: `PascalCase`
- 메서드·변수·파라미터: `camelCase`
- 상수: `UPPER_SNAKE_CASE`
- 패키지: 소문자. 단어는 계층으로 구분하되, 베이스 패키지 `running_service`의 언더스코어는 유지한다.
- 이름은 동작·역할이 드러나도록 짓고, 축약어·의미 없는 이름(`data`, `info`, `temp`)은 피한다.

## 주석

- 명세 문서를 인용하지 않는다 — 절 번호(`5-D`)·문서명(`erd.md`) 모두. 명세가 개편되면 낡으므로 인용 없이 자립하는 문장으로 적는다.
- 보류·미완성은 `// TODO: 친구 목데이터 — 친구 요청·수락·삭제 API를 만들 때 실제 조회로 교체한다`처럼 적는다.

## 테스트

- 새로운 비즈니스 로직에는 단위 테스트를 작성한다.
- 버그 수정 시 실패를 재현하는 테스트를 먼저 추가한다.
- 테스트는 given-when-then 구조로 작성하고, 구분 주석을 `// given`, `// when`, `// then`으로 통일한다. 실행과 검증이 한 문장인 경우에는 `// when & then`으로 합칠 수 있다. 토큰 뒤에 `-> 설명`으로 의도를 덧붙일 수 있다.
- 테스트 메서드 이름과 `@DisplayName`은 행위가 드러나도록 작성한다.
- 내부 구현보다 외부에서 관찰 가능한 결과를 검증한다.
- 불필요한 mock과 과도한 `verify()`를 사용하지 않는다.
