# Runiverse Backend

> 멀리 떨어져 있어도 함께 달리는 원격 동반 러닝 플랫폼, **Runiverse**의 API 서버입니다.

클라이언트는 Flutter 모바일 앱입니다. 안드로이드를 먼저 내고 iOS는 이후에 출시합니다.

## 기술 스택

| 분류 | 사용 기술 |
|---|---|
| Language / Framework | Java 21, Spring Boot 4.1 |
| Security | Spring Security, OAuth2 Resource Server(JWT), Argon2 |
| Data | PostgreSQL, Spring Data JPA, Redis |
| Realtime | WebSocket |
| Infra | AWS (EC2 Auto Scaling, ALB, ECR, S3, SES, SSM Parameter Store) |
| CI/CD | GitHub Actions (OIDC → AWS) |
| Observability | Actuator, Micrometer, Prometheus, Grafana, Filebeat |
| Test | JUnit 5, Mockito, Docker Compose 기반 E2E (LocalStack), k6 부하 테스트 |

## 프로젝트 구조

```text
runiverse-backend
├── running-service   # Spring Boot API 서버 (클린 아키텍처 + DDD)
├── e2e_test          # Docker Compose 기반 E2E 테스트
├── load_test         # k6 부하 테스트 (러닝 상태 저장소 Redis vs RDS 비교)
├── infra             # 로그 수집(Filebeat) 설정
└── docs              # 명세·컨벤션 문서
```

## 시작하기

```bash
cd running-service
./gradlew test      # 전체 테스트
./gradlew bootRun   # 앱 실행 (컨텍스트 경로: /api/v1)
```

> 앱 실행에는 `running-service/.env` 파일이 필요합니다. 단위 테스트는 `.env` 없이 돌아갑니다.

```bash
./e2e_test/run-e2e.sh   # E2E — 배포 이미지를 Docker로 띄워 실행
```

## 부하 테스트

러닝 중 상태(좌표·누적 거리·참가자 상태·콤보)를 Redis에 둘 때와 RDS에 둘 때를 k6로 같은 부하에서 비교했습니다.
1,000명 동시 러닝까지 두 저장소 모두 전원 성공했고 응답 속도도 비슷했습니다. RDS로 옮겨도 pub/sub 때문에 Redis가 남아 비용이 줄지 않아 **Redis를 유지하고 AOF를 켜기로** 했습니다.
구성과 실행 순서는 [load_test/README.md](load_test/README.md), 측정 결과는 [load_test/RESULTS.md](load_test/RESULTS.md)에 있습니다.

## 브랜치 전략 · 배포

| 브랜치 | 역할 | 동작 |
|---|---|---|
| `<type>/<description>` | 기능 개발 | PR 시 단위 테스트 |
| `dev` | 통합 | 테스트 → 이미지 빌드 → E2E → 테스트 서버 배포 |
| `main` | 운영 | `dev` PR만 허용, 머지되면 프로덕션 롤링 배포 |

## 문서

| 문서 | 내용 |
|---|---|
| [architecture.md](docs/architecture.md) | 레이어 규칙 · 구현 스타일 |
| [api-spec.md](docs/api-spec.md) | API 명세 |
| [erd.md](docs/erd.md) | DB 스키마 |
| [feature-spec.md](docs/feature-spec.md) | 기능 명세 · 도메인 제약 |
| [api-convention.md](docs/api-convention.md) | 에러 포맷 · 페이지네이션 · 인증 |
| [code-convention.md](docs/code-convention.md) | 네이밍 · 주석 · 테스트 |
| [git-convention.md](docs/git-convention.md) | 커밋 · 브랜치 · PR |
| [logging-convention.md](docs/logging-convention.md) | 로그 규칙 |
| [metrics-convention.md](docs/metrics-convention.md) | 메트릭 규칙 |

## 팀원

| <img src="https://github.com/jihwanjo-98.png" width="120"> | <img src="https://github.com/KimDwDev.png" width="120"> | <img src="https://github.com/zxc88kr.png" width="120"> |
|:---:|:---:|:---:|
| **조지환** | **김동완** | **박찬** |
| [@jihwanjo-98](https://github.com/jihwanjo-98) | [@KimDwDev](https://github.com/KimDwDev) | [@zxc88kr](https://github.com/zxc88kr) |
| AI / Frontend | Backend | Backend |

## 라이선스

이 프로젝트는 [MIT License](LICENSE)를 따릅니다.
