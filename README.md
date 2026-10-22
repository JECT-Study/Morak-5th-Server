# Morak-5th-Server

젝트 5기 7팀 BE

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

| 계층 | 책임 |
| --- | --- |
| presentation | HTTP 요청 검증과 응답 변환. application의 서비스를 호출한다. |
| application | 유스케이스 실행 순서와 트랜잭션 관리. domain의 규칙과 Repository 인터페이스를 사용한다. |
| domain | 업무 규칙, 엔티티 상태 변경, 저장소 계약, 도메인 예외를 정의한다. |
| infrastructure | Repository 구현, Spring Data JPA, 외부 시스템 연동을 담당한다. |

- domain은 presentation, application, infrastructure를 참조하지 않는다.
- application은 presentation의 DTO나 infrastructure의 구체 구현을 참조하지 않는다.
- HTTP 요청·응답 DTO는 presentation/dto에, HTTP와 독립적인 유스케이스 입출력은 application/dto에 둔다.
- 업무 조건 판단과 상태 변경은 domain/entity에, 작업 흐름은 application/service에 둔다.
- 기술 독립적인 Repository 인터페이스는 domain/repository에 둔다. 이 인터페이스는 JpaRepository를 상속하지 않는다.
- Spring Data JPA 인터페이스와 도메인 Repository 구현체는 infrastructure/persistence에 둔다.
- JPA 매핑은 domain/entity에 허용한다. 별도 영속성 모델과 변환 계층은 기본으로 만들지 않는다.
- 도메인 간 데이터 참조는 ERD의 ID 참조를 기본으로 한다. 여러 도메인을 사용하는 작업은 application에서 조율하며 순환 의존성을 피한다.
- 소셜 로그인 연동은 auth/infrastructure/oauth, 푸시 발송은 notification/infrastructure/push에 둔다.
- common에는 공통 응답, 예외 처리, 로깅 등 공통 코드를 둔다. 기능별 업무 규칙은 해당 feature에 둔다.
- 위 규칙은 현재 패키지와 문서로 정한 개발 규칙이며, 빌드 모듈이나 자동 의존성 검사로 강제하지는 않는다.

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

```powershell
.\gradlew.bat compileJava compileTestJava spotlessCheck
```
