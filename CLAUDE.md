# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build / Run / Test

```bash
# Compile and run (dev mode with hot reload via spring-boot-devtools)
./mvnw spring-boot:run

# Compile only
./mvnw compile

# Run all tests
./mvnw test

# Run a single test class
./mvnw -Dtest="BigEventApplicationTests" test

# Package as JAR
./mvnw package
```

The app starts on the default Spring Boot embedded Tomcat port (8080). The single-page Vue 3 frontend at `src/main/resources/static/index.html` is served directly by Spring Boot as a static resource.

**Configuration**: `application.properties` is minimal — only `spring.application.name=big_event`. Real config (DB, Redis, OSS) is expected from external config or environment variables in deployment. For local dev, create a more complete `application-local.properties` or pass `--spring.profiles.active=local`.

## Architecture Overview

A **Spring Boot 3.2.5** + **Java 17** full-stack blog platform. Artifact name is `mini-blog`; the base package is `com.itheima.big_event`.

### Tech Stack
- **Persistence**: MyBatis 3 (`mybatis-spring-boot-starter`) + MySQL (`mysql-connector-j` 8.0.33) — no JPA
- **Cache**: Redis (`spring-boot-starter-data-redis`) with a custom `CacheClient` handling mutex locks and logical expiration
- **Auth**: JWT tokens (`java-jwt` from Auth0), stored in Redis with an expiration of 30 minutes; refreshed on each request via `TokenRefreshInterceptor`
- **Frontend**: Vue 3 + Vue Router 4 + Axios, loaded from CDN (`unpkg.com`), single `index.html` (~650 lines of inline JS/CSS)
- **Pagination**: PageHelper (`pagehelper-spring-boot-starter`)
- **File Upload**: Qiniu (七牛云) OSS (`qiniu-java-sdk`)
- **Utilities**: Lombok, Hutool, FastJSON, Gson
- **Validation**: Bean Validation (`spring-boot-starter-validation`) with a custom `@State` constraint

### Request Flow

```
Request → TokenRefreshInterceptor (order 0, refreshes Redis TTL)
        → LoginInterceptor (order 1, validates JWT+Redis, populates ThreadLocal)
        → Controller → Service → Mapper → DB
        ← Response wrapped in Result<T>
```

- `TokenRefreshInterceptor` (order=0): if Authorization header present and token exists in Redis, extends its TTL. Always passes through.
- `LoginInterceptor` (order=1): validates JWT against Redis key `login:token:{token}`. Puts claims (`id`, `username`, `role`, `userStatus`) into `ThreadLocalUtil`. Excludes `/user/login` and `/user/register`.
- After request completion, `LoginInterceptor.afterCompletion` clears the ThreadLocal.

Both interceptors are registered in `WebConfig`.

### Package Layout

| Package | Purpose |
|---|---|
| `controller/` | REST controllers: `UserController`, `ArticleController` (main blog), `AdminController`, `CategoryController`, `FileUploadController`, `LogController` |
| `service/` + `service/Impl/` | Business logic layer |
| `mapper/` | MyBatis mapper interfaces (XML sql in `src/main/resources/mapper/`) |
| `pojo/` | Domain objects: `User`, `Article`, `Comment`, `Like`, `Category`, `OperationLog`, `Result<T>`, `PageBean<T>` |
| `DTO/` | Transfer objects: `userDTO`, `ArticleCountDTO` |
| `config/` | `WebConfig` (interceptors), `AsyncConfig` (thread pool), `OssConfig` (Qiniu credentials) |
| `interceptors/` | `LoginInterceptor`, `TokenRefreshInterceptor` |
| `aspect/` | `OperationLogAspect` (AOP logging), `RequireAdminAspect` (AOP permission check) |
| `anno/` | Custom annotations: `@Log`, `@RequireAdmin`, `@State` |
| `validation/` | `StateValidation` — custom validator bean for `@State` |
| `utils/` | `JwtUtil`, `ThreadLocalUtil`, `CacheClient`, `RedisConstants`, `FNossUtil`, `SimpleReidesLock`, `ILock` |
| `exception/` | `PermissionException`, `GlobalExceptionHandler` |

### Key Design Patterns

**Unified Response**: Every controller returns `Result<T>` (code 0 = success, 1 = error, plus msg and optional data). Static factory methods `Result.success(data)` and `Result.error(msg)` simplify construction.

**Admin Authorization**: `@RequireAdmin` annotation on a controller class applies an AOP `@Before` check (`RequireAdminAspect`) — reads `role` from `ThreadLocalUtil.get()`, throws `PermissionException` if not `"admin"`. The `AdminController` and `LogController` are annotated this way.

**Operation Logging**: `@Log(module, operation)` annotation on methods triggers `OperationLogAspect.around()` — captures method name, params, user info, IP, execution time, and status. Saves via `OperationLogMapper.insert()` synchronously in the aspect (not yet async despite `AsyncConfig` being present).

**ThreadLocal User Context**: After login, `LoginInterceptor` stores JWT claims in `ThreadLocalUtil`. Services read the current user via `ThreadLocalUtil.get()` rather than passing it as a parameter. Always cleared after request completion.

**Caching Strategy** (`CacheClient`):
- **Article detail**: mutex lock pattern (`articleDetailMutexLock`) — on cache miss, acquires a Redis SETNX lock before querying DB, prevents cache breakdown. Stores null values for 1 minute to prevent cache penetration.
- **Home list (pages 1-3)**: logical expiration pattern (`queryWithLogicalExpirePage`) — cached data carries an `expireTime` field; when expired, returns stale data and rebuilds cache asynchronously via `CACHE_REBUILD_EXECUTOR`. Pages 4+ bypass cache entirely.
- **General logical expire** (`queryWithLogicalExpire`): similar pattern with async rebuild.
- **Lock implementation**: `tryLockWithRetry` with configurable retry count and interval. The standalone `SimpleReidesLock` class provides a Lua-script-based unlock for atomic release.

**Article enrichment**: `enrichCounts()` queries like/comment/view counts separately via `ArticleCountDTO` and sets them on the `Article` object — these counts are not stored directly on the article row.

### Data Model Notes

- Articles have states controlled by `@State` validation: `"草稿"` (draft) or `"已发布"` (published).
- Users have `userStatus`: `0` = normal, non-zero = banned.
- User roles: `"user"` or `"admin"`.
- Each new user gets a default "未分类" (uncategorized) category.
- `Comment.id` is `Long`, while most other entity IDs are `Integer`.

### Caution Points

- `FNossUtil` has **hardcoded Qiniu credentials** (`accessKey`, `secretKey`) — these should be moved to `OssConfig` properties.
- The login/register endpoints are **swapped**: `POST /user/login` actually registers, `POST /user/register` actually logs in. Be careful when modifying.
- MyBatis mapper XML files live under `src/main/resources/mapper/` and also under `src/main/java/com/itheima/big_event/mapper/` (note: some filenames have a leading space, e.g. ` operationLogQueryMapper.xml`). The POM build section includes `src/main/java/**/*.xml` in resources to handle this.
- No database migration tool (Flyway/Liquibase) — schema must be set up manually.
- The `CacheClient` uses `StringRedisTemplate`, so all cached values are JSON strings (via Hutool's `JSONUtil`).
- **Author display name fix**: `ArticleServiceImpl` has a `getUserDisplayName(User)` helper that falls back to `username` when `nickname` is null (nickname is not set at registration). All 4 places that set `article.authorName` use this helper instead of calling `getNickname()` directly. See [[author-name-fallback]].
