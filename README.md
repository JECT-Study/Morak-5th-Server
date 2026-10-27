# Morak-5th-Server

젝트 5기 7팀 BE

같은 목표로 모여 매일의 실천을 이어가는 자기계발 챌린지 커뮤니티 **모락**의 백엔드 저장소다.

공부, 운동, 생활 습관 등 혼자 미루기 쉬운 도전을 함께 시작하고, 사진 인증과 응원으로 꾸준히 실천하는 경험을 제공한다.

## 주요 기능

아래는 기획 기준의 주요 기능이며, 현재는 프로젝트 실행 환경, 공통 모듈, 기능별 패키지 구조를 구성한 단계다.

- **챌린지 개설·참여**: 공개 챌린지를 탐색하고 지원하거나, 비공개 초대로 지인과 함께 도전한다.
- **일일 미션 인증**: 사진으로 실천을 기록하고, 인증 피드와 좋아요로 서로를 응원한다.
- **그룹 채팅**: 챌린지 참여자들이 실시간 텍스트 대화를 나눈다.
- **무료 포인트**: 출석·광고 등으로 포인트를 얻고, 챌린지 참여와 달성 결과에 따라 사용·정산한다. 실제 결제나 현금 출금은 제공하지 않는다.
- **프로필과 성취 기록**: 개인 인증률, 인증률에 따른 모락이 캐릭터, 완주 메달로 실적을 확인한다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 언어 / 프레임워크 | Java 21 / Spring Boot 4.1.1 |
| 빌드 | Gradle Wrapper 9.8.1 |
| HTTP / JSON / 검증 | Spring MVC, Jackson, Jakarta Validation |
| 데이터 저장 | Spring Data JPA, PostgreSQL |
| 캐시 / 검색 | Spring Data Redis, Spring Data Elasticsearch |
| 인증 / 실시간 통신 | Spring Security, OAuth2 Client·Resource Server, WebSocket |
| DB 마이그레이션 | Flyway |
| 운영 지원 | Spring Boot Actuator |
| 테스트 | JUnit Jupiter, Spring Boot Test, Testcontainers |
| 포맷 / CI | Spotless 8.10.4, GitHub Actions |

## 로컬 실행

- JDK 21을 준비한다. 빌드와 실행에는 저장소의 Gradle Wrapper를 사용한다.
- 전체 테스트 및 Testcontainers 기반 개발 실행에는 실행 중인 Docker 환경이 필요하다. 처음 실행할 때 PostgreSQL·Redis 이미지를 내려받는다.
- 현재 `application.properties`에는 애플리케이션 이름만 설정되어 있다. 일반 실행에는 PostgreSQL 연결 설정이 필요하며, Redis·Elasticsearch·OAuth2 등은 사용하는 기능에 맞춰 설정한다.
- Docker Compose, 공통 환경 변수 예제 파일, 서비스용 인증 설정은 아직 제공하지 않는다. 비밀번호·토큰 등 실제 비밀 값은 저장소에 커밋하지 않는다.

### 직접 준비한 PostgreSQL로 실행

아래는 Windows PowerShell 예시다. 접속 정보는 로컬 DB에 맞게 바꾼다.

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/morak"
$env:SPRING_DATASOURCE_USERNAME = "morak"
$env:SPRING_DATASOURCE_PASSWORD = "<로컬 DB 비밀번호>"
.\gradlew.bat bootRun
```

macOS·Linux에서는 환경 변수를 설정한 뒤 `./gradlew bootRun`을 사용한다.

### Testcontainers로 개발 실행

Docker 실행 후 IDE에서 `src/test/java/com/morak/demo/TestDemoApplication.java`의 `main`을 실행한다. PostgreSQL·Redis 컨테이너의 접속 정보는 `@ServiceConnection`으로 연결된다. 컨테이너 이미지는 현재 `latest` 태그를 사용한다.

위 내용은 현재 코드에 구성된 실행 경로다. 전체 서비스 연동과 로그인 흐름의 실행 검증은 별도로 필요하다.

## 패키지 구조

ERD의 업무 단위를 기준으로 기능을 묶고, 각 기능 내부에 4계층을 둔다.

```text
com.morak
├─ MorakApplication
├─ common
│  ├─ entity
│  ├─ exception
│  ├─ response
│  └─ filter
└─ feature
   ├─ user
   ├─ auth
   ├─ challenge
   ├─ participation
   ├─ certification
   ├─ medal
   ├─ chat
   ├─ notification
   └─ point
```

각 기능은 다음 패키지 구성을 사용한다. 현재는 개발을 위한 패키지 뼈대이며 엔티티, API, 저장소 구현 및 DB 마이그레이션은 포함하지 않는다.

```text
feature/<기능>
├─ presentation
│  ├─ controller
│  └─ dto
│     ├─ request
│     └─ response
├─ application
│  ├─ service
│  └─ dto
├─ domain
│  ├─ entity
│  ├─ repository
│  └─ exception
└─ infrastructure
   └─ persistence
```

## 계층별 책임과 의존성

하나의 기능을 **요청 받기 → 작업 진행하기 → 업무 규칙 판단하기 → DB·외부 서비스 연결하기**로 나눠 작성한다.

| 계층 | 하는 일 | 넣는 코드 예시 |
| --- | --- | --- |
| `presentation` | 요청을 받고, 입력 형식을 검사한 뒤 결과를 응답한다. | Controller, 요청·응답 DTO |
| `application` | 한 작업에 필요한 조회·판단·저장을 순서대로 진행하고 트랜잭션을 관리한다. | 챌린지 신청 Service, 서비스 입출력 DTO |
| `domain` | 서비스의 업무 규칙을 판단하고 데이터의 상태를 바꾼다. | Entity, 도메인 예외, Repository 인터페이스 |
| `infrastructure` | 데이터를 실제로 저장·조회하거나 외부 서비스와 통신한다. | JPA Repository, 저장소 구현체, OAuth·푸시 연동 |

### 예시: 챌린지 참가 신청

아래 이름은 역할을 설명하기 위한 구현 예시다.

1. **presentation**의 `ParticipationController`가 신청 요청을 받고, 각오 한마디 등 입력 형식을 검사한다.
2. **application**의 `ApplyParticipationService`가 챌린지와 기존 신청 내역을 조회하고, 신청에 필요한 작업을 진행한다. 여러 DB 변경이 함께 성공하거나 취소되도록 트랜잭션도 여기서 관리한다.
3. **domain**의 엔티티가 모집 중인지, 신청 가능한 상태인지 등 업무 규칙을 판단하고 신청 상태를 만든다. 서비스는 필요한 엔티티의 동작을 호출한다.
4. 서비스가 **domain**에 정의된 `ParticipationRepository` 인터페이스로 저장을 요청하면, **infrastructure**의 구현체가 실제 DB 저장을 수행한다.
5. **presentation**이 서비스의 결과를 응답 DTO와 공통 응답 형식으로 바꿔 반환한다.

입력값의 길이·필수 여부 같은 **형식 검사**는 presentation에, 모집 상태·신청 자격 같은 **업무 판단**은 domain에 둔다. 이 판단에 필요한 데이터를 가져오고 전체 순서를 연결하는 코드는 application에 둔다.

### 서로 참조하는 방향

여기서 화살표는 **왼쪽 코드가 오른쪽 타입을 가져다 쓴다(import한다)**는 뜻이다. 위 예시의 실행 순서와는 구분한다.

```text
presentation   → application → domain
infrastructure → domain
```

- Controller는 application의 서비스를 호출한다.
- application은 domain의 엔티티와 Repository 인터페이스를 사용한다. Controller의 DTO나 DB 저장 구현체를 직접 사용하지 않는다.
- infrastructure는 domain의 Repository 인터페이스를 구현한다. Spring이 이 구현체를 서비스에 주입하므로, 서비스는 구체 구현체를 몰라도 저장을 요청할 수 있다.
- domain은 나머지 세 계층의 코드를 사용하지 않는다.

예를 들어 `domain/repository/ParticipationRepository`에는 필요한 조회·저장 메서드만 선언한다. `JpaRepository`를 상속하는 인터페이스와 이를 사용하는 구현체는 `infrastructure/persistence`에 둔다.

### 파일 위치를 정할 때

| 작성할 코드 | 위치 |
| --- | --- |
| HTTP 요청·응답에 쓰는 DTO | `presentation/dto/request`, `presentation/dto/response` |
| 서비스에 전달할 값과 서비스 결과를 담는 DTO | `application/dto` |
| 작업 순서와 트랜잭션을 관리하는 서비스 | `application/service` |
| 업무 데이터와 상태 변경 규칙 | `domain/entity` |
| 업무상 실패를 나타내는 예외·오류 코드 | `domain/exception` |
| 필요한 조회·저장 기능을 선언한 인터페이스 | `domain/repository` |
| Spring Data JPA 인터페이스와 저장소 구현체 | `infrastructure/persistence` |
| 소셜 로그인 제공자와 통신하는 코드 | `auth/infrastructure/oauth` |
| 외부 푸시 서비스로 발송하는 코드 | `notification/infrastructure/push` |

이 프로젝트에서는 domain 엔티티에 `@Entity` 같은 JPA 매핑을 붙여 사용한다. 기본적으로 DB 저장용 클래스를 따로 만들지는 않는다.

서로 다른 기능의 데이터를 연결할 때는 ERD의 ID 참조를 기본으로 한다. 여러 기능이 필요한 작업은 application에서 조율하며, 서로의 서비스를 순환해서 호출하지 않도록 한다.

공통 응답·예외 처리·로깅처럼 여러 기능에서 함께 사용하는 코드는 `common`에 둔다. 챌린지 신청 조건처럼 특정 기능의 업무 규칙은 해당 `feature`에 둔다.

## ERD 테이블 소유 도메인

기준: 모락.sql의 17개 테이블. 테이블 이름은 ERD의 실제 이름을 유지한다.

| 도메인 | 테이블 | 책임 |
| --- | --- | --- |
| user | users | 회원 프로필, 약관 동의, 온보딩, 탈퇴 |
| auth | social_account, refresh_token | 소셜 계정 연동, 로그인 토큰 관리 |
| challenge | challenge | 챌린지 모집, 초대, 진행 기간과 정원 |
| participation | participation, participation_result | 참가 신청과 승인, 보증금 상태, 퇴장과 완주, 종료 결과 |
| certification | certification, certification_image, certification_reaction | 인증 제출과 판정, 이미지, 리액션 |
| medal | medal, user_medal | 메달 종류와 획득 이력 |
| chat | chat_message | 챌린지 채팅 메시지 |
| notification | notification, push_subscription, push_token | 인앱 알림, 푸시 수신 설정, 기기 토큰 |
| point | point_account, point_transaction | 포인트 잔액, 보증금 납부와 환급 거래 원장 |

기존 verification은 ERD 용어에 맞춰 certification으로 변경했다. 별도 테이블이 없는 team 패키지는 두지 않으며, 챌린지와 참여자에서 관련 개념을 다룬다. participation_result는 참여자의 종료 결과이므로 participation에, user_medal은 메달 획득 이력이므로 medal에 포함한다.

## 구현 시 참고할 ERD 규칙

- 공통 BaseEntity는 created_at과 updated_at이 모두 있는 엔티티에서 필요할 때 사용한다. 모든 테이블에 일괄 적용하지 않는다.
- certification은 참가자와 날짜 조합, participation은 챌린지와 회원 조합의 유일성을 유지한다.
- point_account의 잔액 변경은 조건부 UPDATE로 처리하며, point_transaction은 수정·삭제하지 않는 거래 원장으로 다룬다.
- 거래의 idempotency_key와 알림의 dedup_key 등 중복 방지 제약은 실제 구현과 마이그레이션에서 반영한다.
- API collection 경로는 /api/v1/challenges처럼 복수형 kebab-case를 사용하며, 도메인 오류 코드는 도메인 이름을 접두사로 사용한다.

## 검증

Windows PowerShell 기준 명령이다. macOS·Linux에서는 `.\gradlew.bat`을 `./gradlew`로 바꾼다.

### 컴파일 및 포맷 검사

```powershell
.\gradlew.bat compileJava compileTestJava spotlessCheck
```

`compileTestJava`는 테스트 코드를 컴파일하며, 테스트 자체를 실행하지는 않는다.

### 포맷 자동 정리

```powershell
.\gradlew.bat spotlessApply
```

Java 코드는 4칸 들여쓰기를 탭으로 변환하고, 줄 끝 공백과 파일 끝 개행을 정리한다. Gradle·Markdown·워크플로 파일도 공백과 개행 검사 대상이다. 자동 정리 후 변경 내용을 확인한다.

### 테스트 실행

```powershell
# Docker 없이 예외 처리 테스트만 실행
.\gradlew.bat test --tests com.morak.common.exception.GlobalExceptionHandlerTest

# Docker가 필요한 컨텍스트 테스트를 포함해 전체 테스트 실행
.\gradlew.bat test
```

예외 처리 테스트는 400·404·415 상태 유지, 405 상태와 Allow 헤더 보존, 예상하지 못한 예외의 500 응답을 확인한다. 컨텍스트 테스트는 PostgreSQL·Redis Testcontainers를 등록해 Spring 컨텍스트 로딩을 확인한다.

### CI 범위

현재 `.github/workflows/ci.yml`은 JDK 21 환경에서 `spotlessCheck`만 실행한다. CI 성공이 컴파일·테스트 통과를 의미하지 않으므로 PR 작성 전 관련 컴파일과 테스트를 로컬에서 실행하고 결과를 PR에 기록한다.

## 공통 응답과 예외 처리

컨트롤러 응답에는 `common/response/ApiResponse`를 사용한다. 다음은 응답 구조 예시다.

```json
{
  "success": true,
  "data": { "id": 1 },
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON_001",
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

- 성공은 `ApiResponse.success(data)`, 빈 성공 응답은 `ApiResponse.successEmpty()`를 사용한다.
- 업무 예외는 `ErrorCode`를 구현한 오류 코드와 `BusinessException`으로 표현한다.
- `GlobalExceptionHandler`는 업무 예외, 요청 검증 실패, JSON 읽기 오류, 예상하지 못한 예외를 공통 응답으로 변환한다.
- Spring의 `ErrorResponse` 구현 예외는 원래 HTTP 상태와 응답 헤더를 유지한다. 현재 404는 `COMMON_002`, 그 외 4xx는 `COMMON_001`, 5xx는 `COMMON_003`으로 매핑한다.
- 이 응답 규칙은 MVC 예외 처리 범위에 적용된다. Spring Security 필터에서 발생하는 인증·인가 실패까지 공통 응답으로 처리하는 설정은 아직 없다.

## 요청·응답 로깅

`RequestResponseLoggingFilter`는 HTTP 메서드, 경로, 응답 상태, 처리 시간과 조건에 맞는 JSON 본문을 기록한다.

- 본문 크기가 4,096바이트 이상이거나 JSON이 아니면 본문 로그를 생략한다.
- JSON 필드 이름에 `password`, `passwd`, `token`, `authorization`, `secret`, `credential`이 포함되면 대소문자와 관계없이 값을 마스킹한다.
- 이 규칙이 모든 개인정보를 가려 주는 것은 아니다. 새 민감 필드를 추가하거나 별도로 로그를 작성할 때는 노출 여부를 확인한다.
