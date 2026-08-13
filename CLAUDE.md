# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build
./gradlew clean build

# Run
./gradlew bootRun

# Test
./gradlew test
```

> Integration tests require Docker (Testcontainers: MySQL, Neo4j, MongoDB).

## 문서 지도 (Document Map)

작업 시작 전 아래 문서를 참조하여 필요한 규칙을 확인한다.

| 문서 | 경로 | 참조 시점 |
|------|------|---------|
| 작업 파이프라인 | `harness/WORKFLOW.md` | 모든 작업 시작 전 — 계획(PLAN.md) 작성 → 승인 → 브랜치 → 구현 → 커밋 순서 준수 |
| 아키텍처 & 코딩 컨벤션 | `harness/ARCHITECTURE.md` | 새 파일·기능 추가 시 — 계층 분리, DDD/Hexagonal 규칙, 도메인 간 통신 방식 |
| 테스트 프로토콜 | `harness/TESTING-GUIDE.md` | 테스트 코드 작성 시 — Base 클래스 상속, Given-When-Then 형식, Edge Case 검증 |

**작업 규칙 요약**
- `PLAN.md`는 프로젝트 루트(`/`)에 생성한다.
- 브랜치는 반드시 `main`에서 분기하며, 네이밍은 `ai/feat-[기능명]` 또는 `ai/fix-[버그명]`을 따른다.
- 명시적 승인(Approve) 전까지 코드를 작성하거나 수정하지 않는다.

## Architecture

**DunbarHorizon** is a Spring Boot 3.4.0 / Java 21 REST API using **hexagonal architecture** (Ports & Adapters).

### Module Structure

Each domain module follows this layered pattern:
```
{domain}/
├── adapter/in/web/      # REST Controllers, request/response DTOs
├── adapter/out/         # Persistence adapters, external service adapters
├── application/
│   ├── dto/             # Input/output DTOs for use cases
│   ├── port/in/         # Input port interfaces (use cases)
│   ├── port/out/        # Output port interfaces (repositories)
│   └── service/         # Use case implementations
└── domain/              # Entities, value objects, domain exceptions
```

Domain modules:
- `account/` — User signup, login, email verification, OAuth2 Google
- `buzz/` — Content casting/sharing (core social feature), MongoDB-backed
- `flag/` — Content moderation (flags, comments, memorials)
- `social/` — Friendships, friend requests, labels, social network analysis (Neo4j)
- `notification/` — Push notifications via Firebase FCM, MongoDB-backed
- `trace/` — User activity tracing (who visited whom)
- `global/` — Cross-cutting: security, DB config, exception handling, annotations, domain events, base entities

### Multi-Database Design

| Database | Purpose | Config |
|----------|---------|--------|
| MySQL (JPA/Hibernate) | Relational: users, flags, traces, notification tokens | `global/config/database/JpaConfig.java` |
| Neo4j | Graph: friendship networks, labels, social connections | `global/config/database/Neo4jConfig.java` |
| MongoDB | Documents: buzz content, notification history | `global/config/database/MongoConfig.java` |

**JPA base packages:** `account`, `buzz`, `trace`, `notification.adapter.out.persistence.jpa`, `flag`
**Neo4j base packages:** `social`
**MongoDB base packages:** `notification.adapter.out.persistence.mongo`, `buzz.adapter.out.persistence.mongo`

Local defaults:
- MySQL: `localhost:3306/dunbar_horizon`
- Neo4j: `bolt://localhost:7687`, database: `neo4j`
- MongoDB: `localhost:27017/dunbar_horizon_notification`

### Authentication

- **핵심 원칙:** 증명되기 전에는 신원 키가 아니다. 이메일 소유가 증명된 뒤에만 계정이 생성되므로
  `Auth.verified` 같은 플래그가 존재하지 않고, 로컬과 OAuth가 단일 모델이다.
- **JWT (email/password):** Tokens stored as HTTP-only cookies (`access_token`, `refresh_token`). HMAC-SHA512. Refresh token default TTL: 7 days (604800s).
- **OAuth2 (Google):** `CustomOAuth2UserService`(공급자 `email_verified` 검증) → `OAuth2AuthenticationSuccessHandler` issues JWT cookies.
- **로그인 실패는 세 경우 모두 동일한 401 + 동일 본문**(계정 열거 차단). 사유는 로그로만 남긴다.
- Public endpoints: `POST /api/auth/users`, `POST /api/auth/tokens`, `PATCH/DELETE /api/auth/tokens`, `POST /api/auth/verifications`, `GET /api/auth/verifications/*`, `/oauth2/**`, `/login/oauth2/**`
- All other endpoints require authentication.
- Roles: `ROLE_USER`, `ROLE_ADMIN`.
- `@CurrentUserId` — custom parameter annotation resolving authenticated user's ID from JWT in controllers.

### API Conventions

- REST JSON API: `201` on creation, `200` on success, `204` on no-content.
- Auth endpoints: `/api/auth/`
- Feature endpoints: `/api/v1/{domain}/`
- Paginated responses: `Slice<T>`
- Error response shape: `{ "error": "ExceptionClassName", "message": "...", "validation": {...} }`

## API Endpoints Reference

### Account (`/api/auth/`)
```
POST   /api/auth/verifications            # 가입 접수 + 메일 발송 (재요청 = 재발송)
GET    /api/auth/verifications/{token}    # 토큰 유효성 확인 → { email }
POST   /api/auth/users                    # 가입 완료: {token, password, nickname} → 계정 생성 + 쿠키
POST   /api/auth/tokens                   # login → JWT cookies
DELETE /api/auth/tokens                   # logout
PATCH  /api/auth/tokens                   # refresh tokens
```

> 로컬 가입은 **사전 인증** 방식이다. 이메일 소유가 증명되기 전에는 `users`/`auths`에
> 행이 생기지 않으며, 비밀번호는 링크 클릭 후에 입력받는다.

### Social Network (`/api/v1/networks`)
```
GET /api/v1/networks/me?circleSize=DUNBAR
    → List<NetworkFriendEdgeResult>  # default intimacy network (Soft Morphing)

GET /api/v1/networks/labels/{labelName}?circleSize=KINSHIP
    → List<NetworkFriendEdgeResult>  # label-filtered network

GET /api/v1/networks/mutual/one-hop?targetId=..&currentSkeletonIds=[..]
    → List<MutualFriendEdgeResult>   # drag-and-drop friend addition

GET /api/v1/networks/mutual/two-hop?targetId=..&circleSize=DUNBAR
    → List<NetworkOneHopsByTwoHopResult>  # 2-hop recommendations

GET /api/v1/networks/recommendations?anchorId=..
    → List<AnchorExpansionResult>    # anchor expansion suggestions
```

### Friendships (`/api/v1/friends`)
```
GET    /api/v1/friends                   # list all friendships
GET    /api/v1/friends/{friendId}        # friendship detail
PATCH  /api/v1/friends/{friendId}        # update (alias, intimacy, isRoutable)
DELETE /api/v1/friends/{friendId}        # break friendship
```

### Friend Requests (`/api/v1/friend-requests`)
```
GET    /api/v1/friend-requests                 # list incoming requests
POST   /api/v1/friend-requests                 # send request
DELETE /api/v1/friend-requests/{id}            # cancel request
POST   /api/v1/friend-requests/{id}/accept     # accept
POST   /api/v1/friend-requests/{id}/reject     # reject
```

### Labels (`/api/v1/labels`)
```
GET    /api/v1/labels                          # list labels
POST   /api/v1/labels                          # create label
DELETE /api/v1/labels/{labelId}               # delete label
PATCH  /api/v1/labels/{labelId}               # update label
POST   /api/v1/labels/{labelId}/members        # add members
PUT    /api/v1/labels/{labelId}/members        # replace members
DELETE /api/v1/labels/{labelId}/members/{userId} # remove member
```

### Buzz (`/api/v1/buzzes`)
```
POST   /api/v1/buzzes                          # create buzz (manual/label/pivot recipients)
GET    /api/v1/buzzes/                         # received buzzes (paginated)
GET    /api/v1/buzzes/{buzzId}                 # buzz detail
GET    /api/v1/buzzes/senders/unread           # unread sender IDs
POST   /api/v1/buzzes/{buzzId}/replies         # reply
PATCH  /api/v1/buzzes/{buzzId}/replies/{id}    # update reply
DELETE /api/v1/buzzes/{buzzId}/replies/{id}    # delete reply
```

### Trace (`/api/v1/social/traces`)
```
POST  /api/v1/social/traces     # record visit
```

## Key Domain Concepts

### Social Graph (Neo4j)

**Node types:**
- `SocialUser` (label: `USER_REFERENCE`) — synced from account domain
- `Friendship` — intermediate node connecting two users (HEX pattern)
  - Properties: `intimacy`, `interestScore`, `isRoutable`, `createdAt`, `updatedAt`
- `Label` — user-defined friend group
  - Properties: `name`, `exposure`

**Relationship types:**
- `(user)-[:HAS_FRIENDSHIP]->(friendship)<-[:HAS_FRIENDSHIP]-(friend)`
- `(user)-[:OWNS_LABEL]->(label)`
- `(user)-[:ATTACHED_TO]->(label)`

**DunbarCircle enum** (`social/domain/friend/DunbarCircle.java`):
```java
SUPPORT(5), SYMPATHY(15), KINSHIP(50), DUNBAR(150)
```
Maps frontend slider values to network query size limits.

**Network query patterns** (in `SocialNetworkNeo4jRepositoryAdapter`):
- Default network: top N friends by intimacy + their mutual connections
- Stranger Quota: max 5 mutual friends exposed for privacy
- `isRoutable = false` friends are hidden from others' network views

### Neo4j Cypher DSL (current refactor: `refactor/neo4j-pure-cypher`)

Uses `Neo4jClient` + `org.neo4j.cypherdsl` (not Spring Data OGM). Key files:
- `social/adapter/out/neo4j/dsl/SocialNetworkPatterns.java` — type-safe Cypher node/pattern builders
- `social/adapter/out/neo4j/dsl/SocialNetworkProperties.java` — property name constants
- `social/adapter/out/neo4j/dsl/ApocPatterns.java` — APOC library wrappers (path expansion, mutual friend counting)

APOC plugin is required in Neo4j for `apoc.path.expandConfig` queries.

### Buzz Recipients

Three recipient strategies:
- `ManualRecipientSpec` — explicit list of user IDs
- `LabelRecipientSpec` — all members of a label
- `PivotRecipientSpec` — friends-of-friends via pivot user

### Domain Events

Located in `global/event/`:
- `UserActivatedEvent`, `UserDeactivatedEvent` — account lifecycle
- `UserInteractionEvent(actorId, targetId, InteractionType)` — updates Neo4j interest scores
- `MutualInteractionEvent` — mutual interaction tracking
- `NotificationEvent` — triggers FCM push

Aggregate roots extend `BaseTimeAggregateRoot` which publishes events via `@DomainEvents`.

## Key Patterns

- **Soft delete:** `Deletable` / `SoftDeletable` interfaces.
- **Auditing:** `BaseTimeEntity` (JPA) + Neo4j auditing for `createdAt`/`updatedAt`.
- **Async:** `@Async` + Spring Retry for notifications and background tasks.
- **Cross-DB sync:** `SocialUserSyncHelper` keeps MySQL ↔ Neo4j `SocialUser` nodes in sync on account events.
- **Friendship decay:** `FriendshipDecayService` / `FriendshipDecayPolicy` handles time-based intimacy decay.

## Environment Variables

```
# Required
JWT_SECRET_KEY              # Base64-encoded HMAC-SHA512 secret
JWT_ACCESS_EXPIRATION       # Access token TTL (seconds)
JWT_REFRESH_EXPIRATION      # Refresh token TTL (default: 604800)

OAUTH_GOOGLE_CLIENT_ID
OAUTH_GOOGLE_CLIENT_SECRET

SMTP_USER                   # Gmail address
SMTP_PASSWORD               # Gmail app password

MYSQL_PASSWORD              # MySQL root password
NEO4J_PASSWORD

# Optional (have defaults)
MYSQL_URL                   # default: jdbc:mysql://localhost:3306/dunbar_horizon
DB_USERNAME                 # default: root
NEO4J_URI                   # default: bolt://localhost:7687
MONGO_URI                   # default: mongodb://localhost:27017/dunbar_horizon_notification
```

## Spring Profiles

Config is in `src/main/resources/application.yml`.

- `local` (default) — Neo4j Cypher logging at DEBUG; CORS allows `http://localhost:3000`
- `prod` — CORS allows `https://www.dunbarhorizon.com`; JVM: `-Xms256m -Xmx512m`

Switch: `spring.profiles.active` property or JVM flag `-Dspring.profiles.active=prod`.

Dockerfile uses `eclipse-temurin:21-jdk-alpine` and activates the `prod` profile.

## Testing

Testcontainers in `TestContainerConfig.java` spin up:
- `mysql:8.0` (database: `dunbar_horizon_db`, user: `root`, password: `test`)
- `neo4j:5.12` (password: `password`, APOC plugin enabled)
- `mongo:7.0`

All containers use `reuse=true` for faster iteration. Docker must be running.
