# 项目全面审计报告

---

# 一、风险 & 问题排查

## 🔴 严重 · 上线必炸

### 1. `CategoryServiceImpl` 从 ThreadLocal 取用户 ID 的 key 错误

**位置**：`CategoryServiceImpl.java:33,43,64`

```java
// LoginInterceptor 存入的 key 是 "id"
Map<String,Object> Claims=ThreadLocalUtil.get();
Integer userId = (Integer) map.get("userId");  // ❌ key 是 "userId"，实际存的是 "id"
```

LoginInterceptor (`UserController.java:72`) 存的是 `Claims.put("id", u.getId())`，但 `CategoryServiceImpl` 三次都用 `map.get("userId")` 取值，永远拿到 `null`。`category.setCreateUser(null)` 会直接落库，后续所有按 `create_user` 过滤的分类查询全部失效。

**上线故障**：任何用户无法创建/查看/删除自己的分类，整个分类功能完全不可用，新增分类 `create_user` 为 NULL，MySQL 严格模式下插入可能直接报错。

### 2. `FNossUtil` 硬编码七牛云 AccessKey/SecretKey

**位置**：`FNossUtil.java:39-41`

```java
String accessKey = "NBcgMRLhpcdjJE-BmEpgGQwXePhSvhSodw3FeffR";
String secretKey = "Epsdk8pMMrCdOarnJG5sU5im-XtglTDJ67Z6u5ww";
```

密钥明文硬编码在源码中，且 `OssConfig` 已经用 `@ConfigurationProperties(prefix = "oss")` 定义了配置类但完全没有被使用。

**上线故障**：代码一旦提交到任何公开/半公开仓库，密钥立即泄露，七牛云资源可能被盗用、篡改、删除，产生巨额账单。这是安全红线级别的问题。

### 3. 登录/注册接口路由颠倒

**位置**：`UserController.java:44,59`

```java
@PostMapping("/login")    // 实际执行注册逻辑
public Result login(...) { userService.log(userName, password); }

@PostMapping("/register") // 实际执行登录逻辑
public Result register(...) { ... JwtUtil.genToken(Claims); }
```

`/user/login` 做了注册，`/user/register` 做了登录（生成 token 返回）。前端调用完全错乱。

**上线故障**：前端按正常语义调用会导致：注册时实际触发了登录（用户不存在 → 返回"用户不存在"），登录时实际触发了注册（已存在用户 → 返回"用户已经存在"）。整个用户系统不可用。

### 4. `ArticleMapper.xml` 中 `WHERE` 子句 `and` 前置导致全表扫描风险

**位置**：`ArticleMapper.xml:9-10`

```xml
<where>
    and create_user = #{userId}   <!-- and 前置，靠 where 标签自动去除 -->
```

虽然 MyBatis `<where>` 会自动去除前置 `AND`/`OR`，但 `list` 方法的 userId 来自 `ThreadLocalUtil.get().get("id")`，如果 ThreadLocal 被意外清空（如异步调用），`userId` 为 null 时 `<where>` 内无有效条件 → **生成 `SELECT * FROM article` 全表返回**，一个用户可能看到全站所有文章（包括草稿）。

**上线故障**：数据越权泄漏，用户 A 可以看到用户 B 的草稿文章。

### 5. `operationLogQueryMapper.xml` namespace 写错

**位置**：`src/main/resources/com.itheima.big_event/mapper/ operationLogQueryMapper.xml:4`

```xml
<mapper namespace="com.itheima.big_event.mapper.ArticleMapper">  <!-- ❌ 应该是 operationLogQueryMapper -->
```

namespace 指向了 `ArticleMapper`，而接口是 `operationLogQueryMapper`。MyBatis 在启动时会尝试绑定，因为 namespace 和接口全限定名不一致，**这个 XML 中的 SQL 永远不会被加载到正确的 mapper 中**。

**上线故障**：调用 `operationLogQueryMapper.selectByUserNameAndModule()` 时 MyBatis 找不到对应的 XML statement，抛出 `BindingException`，日志查询功能 500 错误。

---

## 🟠 高危 · 并发/数据一致性

### 6. 点赞功能无并发防护（check-then-act 竞态条件）

**位置**：`LikeServiceImpl.java:21-38`

```java
int count = likeMapper.selectByUserIdAndArticleId(userId, articleId); // 1. 查
if (count > 0) {
    likeMapper.deleteByUserIdAndArticleId(userId, articleId);          // 2. 删
    articleMapper.decrLikeCount(articleId);                            // 3. 减计数
} else {
    likeMapper.addByUserIdAndArticleId(userId, articleId);             // 2. 增
    articleMapper.incrLikeCount(articleId);                            // 3. 加计数
}
```

典型 TOCTOU（Time-of-check to time-of-use）漏洞。快速双击按钮：
- 请求 A 查到 count=0 → 插入点赞 → `incrLikeCount`
- 请求 B 查到 count=0（在 A 插入前）→ 也插入点赞 → 再次 `incrLikeCount`

**上线故障**：同一用户多次点赞同篇文章，点赞数虚高；高并发下 like_record 表可能因唯一键报 `DuplicateKeyException`（如果有的话），否则出现重复记录。取消点赞同理可能把点赞数减成负数。

### 7. 评论删除：无权限校验 + 计数错误减

**位置**：`CommentServiceImpl.java:41-54`

```java
commentMapper.deleteComment(id, userId);  // SQL: WHERE id=#{id} AND user_id=#{userId}
articleMapper.decrCommentCount(comment.getArticleId());  // 无论删除是否成功都会执行！
```

`deleteComment` 带 userId 条件，如果用户 A 尝试删用户 B 的评论，SQL 影响行数为 0（不报错），但后续 `decrCommentCount` 照常执行，评论数被错误扣减。

**上线故障**：任意用户可触发文章评论数变为负数；恶意用户可以遍历文章把所有文章的 comment_count 刷成极小的负值。

### 8. 点赞/评论的计数更新与记录写入不在同一事务

`likeArticle`、`addComment` 中 insert/delete 和 `incrXxxCount`/`decrXxxCount` 是两个独立的 SQL，没有 `@Transactional`。

**上线故障**：如果计数更新 SQL 执行失败（如数据库连接断开），出现记录写入成功但计数不更新的不一致状态。

### 9. `AdminServiceImpl.deleteComment` 中 batchDelete + 遍历 update 无原子性

**位置**：`AdminServiceImpl.java:46-64`

虽有 `@Transactional`，但 `countmap` 统计的是"待删除评论数"，如果传进来的 `ids` 中有部分评论不存在（已被其他管理员删除）、或归属的文章已不存在，`countmap` 与最终实际删除数就会有偏差，导致文章评论数扣除不正确。

---

## 🟡 中危 · 安全 & 健壮性

### 10. 密码明文存储

**位置**：`UserServiceImpl.java:34, UserMapper.java:19`

注册时 `user.setPassword(password)` 直接存明文，没有任何哈希（如 BCrypt）。登录对比也是明文对比。

**上线故障**：数据库一旦泄漏，所有用户密码直接暴露；不满足等保要求；无法通过任何安全审计。

### 11. `UserController.updatePassword` 空值校验逻辑反了

**位置**：`UserController.java:150`

```java
if (StringUtils.hasLength(oldPassword) && StringUtils.hasLength(newPassword)
        && StringUtils.hasLength(confirmPassword)) {
    return Result.error("密码不能为空");  // ❌ 当三者都有值时报错"不能为空"
}
```

逻辑完全颠倒：三个字段都有值时反而报错，有一个为空时反而跳过校验继续执行。

**上线故障**：正常用户永远无法修改密码（总是返回"密码不能为空"）；如果任一字段为空则会穿透校验，执行到 `loginUser.getPassword().equals(oldPassword)` 时因 `oldPassword` 为 null 而 NPE。

### 12. 修改密码未加密新旧密码对比

**位置**：`UserController.java:161`

```java
if (!loginUser.getPassword().equals(oldPassword)) { ... }
userService.updatePassword(newPassword);  // 直接存明文 newPassword
```

如果后续加了密码加密，这里新旧密码比较逻辑会直接失效（加密后的旧密码和明文 oldPassword 永远不相等）。

### 13. JWT 密钥过于简单

**位置**：`JwtUtil.java:14`

```java
private static final String KEY = "itheima";
```

纯字母短字符串，暴力破解难度极低。任何人拿到 token 后可以在本地暴力枚举签名密钥，伪造任意用户 token。

### 14. Token 黑名单缺失

用户被封禁（`userStatus != 0`）后，已颁发的 JWT token 在 Redis 中依然有效直到过期（30 分钟），封禁不是即时生效的。登录时存 Redis 的 value 是 `String.valueOf(u.getUserStatus())`（`UserController.java:82`），但拦截器验证时只检查 key 是否存在，不检查 value 的用户状态。

**位置**：`LoginInterceptor.java:30-33`

```java
String userId = stringRedisTemplate.opsForValue().get(redisKey);
if (userId == null) { response.setStatus(401); return false; }
// ❌ 没有检查 value 中的 userStatus 是否为封禁状态
```

**上线故障**：封禁用户后，该用户仍可在 30 分钟内正常操作所有接口。

---

## 🟢 工程规范问题

### 15. 无数据库版本管理

没有 Flyway 或 Liquibase，数据库 schema 靠人工手动维护。多环境部署时 schema 漂移风险极高。

### 16. `UserMapper.findAll` 参数无用

`findAll(Integer pageNum, Integer pageSize)` — 参数传了但 SQL `select * from user` 完全没有用到，纯粹靠 PageHelper 拦截分页。虽然功能正常，但参数具有误导性。

### 17. 操作日志同步写入阻塞请求

`OperationLogAspect` 直接在切面中同步调用 `operationLogMapper.insert(operationLog)`（第 110 行），虽然 `OperationLogService.saveLog()` 已经用 `@Async("logExecutor")` 做了异步封装，但切面完全没有使用它。

**上线故障**：每次记录日志的操作都会增加一次同步 DB 写入的延迟，高并发下拖慢所有接口响应时间。

### 18. Mapper XML 文件命名不规范

- `src/main/resources/mapper/ AdminMapper.xml` — 文件名以空格开头
- `src/main/resources/com.itheima.big_event/mapper/ operationLogQueryMapper.xml` — 同上

在部分文件系统和 CI/CD 工具中可能导致文件找不到。

### 19. 无接口限流/防护

登录、注册、点赞、评论等接口没有任何限流手段（无 RateLimiter、无验证码）。可以被脚本暴力攻击：批量注册垃圾账号、刷赞、刷评论。

### 20. 全局异常处理吞掉异常信息

`GlobalExceptionHandler.java:21-23` — `Exception.class` 兜底返回 `e.getMessage()`，但很多 Spring/MyBatis 异常 message 包含内部 SQL、表结构等敏感信息，直接返回给前端有信息泄漏风险。

### 21. `UserController.updatePassword` throws NPE if oldPassword is null

当校验被绕过（如前述逻辑反转），`loginUser.getPassword().equals(oldPassword)` 中 `oldPassword` 为 null → NPE → 500。

### 22. 首页列表分页 `total` 被注释掉

**位置**：`ArticleServiceImpl.java:117`

```java
// ppb.setTotal(total);  // ❌ 被注释了
```

前端无法获取总页数，分页组件无法渲染"共 N 页"、无法判断是否有下一页。

---

# 二、现有模块升级优化方案

## 1. 认证 & 用户模块

| 优化项 | 方案 | 收益 |
|---|---|---|
| 密码加密 | 引入 BCrypt（`spring-boot-starter-security` 或单独 `jbcrypt`），注册时 hash，登录时 `BCrypt.checkpw()` 比对；修改密码时同样先验证旧密码 hash | 满足安全基线，防止明文泄漏 |
| Token 黑名单即时生效 | LoginInterceptor 校验时读取 Redis value（userStatus），若 `!= 0` 直接 reject；封禁用户时主动 `DELETE` 其 Redis token key | 封禁即时生效 |
| JWT 密钥外部化 | 将密钥移到 `application.properties` 中的 `jwt.secret` 配置项，支持环境变量覆盖；生产环境使用 256-bit 随机密钥 | 安全性大幅提升 |
| 登录限流 | 使用 Redis + AOP 实现登录失败计数器，同一 IP 5 分钟内失败 5 次 → 锁定 15 分钟 | 防暴力破解 |
| 刷新 token 机制 | TokenRefreshInterceptor 在剩余时间 < 5 分钟时颁发新 token，前端收到新 token 后替换 | 用户无感续期 |

## 2. 文章模块

| 优化项 | 方案 | 收益 |
|---|---|---|
| 首页列表 total 修复 | 补回 `ppb.setTotal(total)`；同时 `count` 查询和 `selectByPage` 的条件保持一致 | 前端分页正常 |
| 文章详情缓存预热 | 文章发布/更新后主动写入 Redis（`CacheClient.set`），而非等首次查询时被动加载 | 发布后首次访问无延迟 |
| 浏览量异步批量写入 | 用 Redis `HINCRBY` 记录文章点击量，定时任务（`@Scheduled`）每 5 分钟批量同步到 MySQL | 减少 DB 写压力 |
| 文章搜索 | 引入 Elasticsearch 或 MySQL 全文索引（`FULLTEXT INDEX`），对标题、内容做关键词搜索 | 基础搜索能力 |
| 文章软删除 | `article` 表加 `is_deleted` 字段，删除操作改为标记删除，数据可恢复 | 防误删 |

## 3. 点赞 & 评论模块

| 优化项 | 方案 | 收益 |
|---|---|---|
| 点赞幂等性 | 数据库 `like_record` 表加唯一索引 `UNIQUE(user_id, article_id)`；业务层改为 `INSERT ... ON DUPLICATE KEY` 或捕获 `DuplicateKeyException` 后转为取消赞逻辑 | 杜绝重复点赞 |
| 点赞 Redis 化 | 用 Redis Set `article:like:{articleId}` 存储点赞用户 ID，取消赞 `SREM`，计数用 `SCARD`。定时同步到 MySQL。 | 应对高并发点赞 |
| 评论删除权限修复 | `deleteComment` 返回值判断影响行数；如果为 0 则不执行 `decrCommentCount` | 修复计数 bug |
| 评论敏感词过滤 | 接入 Hutool 的 `SensitiveUtil` 或第三方服务做评论内容审核 | 合规 & 社区治理 |
| 计数一致性 | 用 Redis `HINCRBY` 维护点赞数/评论数，定时与 MySQL 做 reconciliation | 高并发下计数准确 |
| 评论嵌套 | 评论表加 `parent_id`、`reply_to_user_id`，支持二级回复 | 互动深度 |

## 4. OSS 文件上传

| 优化项 | 方案 | 收益 |
|---|---|---|
| 密钥外部化 | `FNossUtil` 改为从 `OssConfig`（已有的 `@ConfigurationProperties`）读取，删除源码中的硬编码 | 安全红线解除 |
| 文件类型/大小校验 | 上传前校验 Content-Type 白名单（仅允许图片）、文件大小上限（如 5MB） | 防恶意上传 |
| 上传进度 & 断点续传 | 利用七牛云 SDK 的分片上传 API | 大文件体验 |
| 多存储源抽象 | 定义 `FileStorage` 接口，实现 `QiniuStorage`、`LocalStorage`、`AliOssStorage`，通过配置切换 | 拓展性 |

## 5. Redis 缓存模块

| 优化项 | 方案 | 收益 |
|---|---|---|
| 缓存穿透保护统一化 | `CacheClient` 中所有查询方法统一加空值缓存逻辑（目前只有 `articleDetailMutexLock` 有） | 防恶意查询不存在的 ID |
| 缓存预热 | 应用启动时用 `@PostConstruct` + `CommandLineRunner` 把首页前 3 页、热门文章预加载到 Redis | 冷启动即有好体验 |
| 缓存雪崩保护 | 过期时间统一加随机偏移（已有部分实现），扩展至所有缓存 key | 避免集中过期打垮 DB |
| 监控埋点 | 缓存命中率统计（AOP + Micrometer），暴露为 `/actuator/metrics` | 可观测性 |

## 6. 权限校验

| 优化项 | 方案 | 收益 |
|---|---|---|
| 细粒度权限 | 当前 `@RequireAdmin` 只有 admin/非 admin 两级。扩展为 RBAC：role 表 + permission 表，注解支持 `@RequirePermission("article:delete")` | 灵活的角色权限 |
| 资源归属校验 | AOP 切面校验"当前用户是否为该资源的 owner"，如修改文章时校验 `article.createUser == currentUserId` | 防止横向越权 |
| 管理员操作审计 | 所有 `AdminController` 方法已有 `@Log`，但敏感操作（封禁/删除）加二次确认机制（独立审计表） | 合规 |

## 7. 代码质量改进

| 优化项 | 方案 |
|---|---|
| 修复接口路由 | 交换 `/user/login` 和 `/user/register` 逻辑，或直接重命名方法 |
| 统一 ThreadLocal key | 定义常量类 `UserContext.KEY_ID = "id"`、`KEY_USERNAME = "username"`，所有地方引用常量而非硬编码字符串 |
| CategoryServiceImpl key 修复 | `map.get("userId")` → `map.get("id")` |
| 修复 updatePassword 校验 | 取反条件 `if (!StringUtils.hasLength(...))` |
| operationLogQueryMapper 修复 | XML namespace 改为 `com.itheima.big_event.mapper.operationLogQueryMapper`，对应 SQL 中的 `operation` 参数与接口 `module` 参数统一并加 `@Param` |
| 操作日志改为异步 | `OperationLogAspect` 改为注入 `OperationLogService` 并调用 `saveLog()`（已有 `@Async`） |
| Mapper XML 文件重命名 | 去除文件名开头的空格 |

---

# 三、项目拓展方向建议

## 🔧 刚需拓展（弥补短板）

### 1. 数据库版本管理 (Flyway)
- **内容**：引入 Flyway，把现有 schema 导出为 V1__init.sql，后续变更用版本化 SQL
- **面试考点**：数据库迁移工具选型 (Flyway vs Liquibase)、CI/CD 中集成 migration、回滚策略、多环境 schema 管理

### 2. 接口文档 (Knife4j / SpringDoc)
- **内容**：引入 `springdoc-openapi-starter-webmvc-ui`，为所有 Controller 生成 Swagger 文档
- **面试考点**：OpenAPI 3.0 规范、RESTful API 设计原则、前后端协作流程

### 3. 单元测试 & 集成测试
- **内容**：对 Service 层写 JUnit 5 + Mockito 单元测试，对 Controller 层写 `@WebMvcTest` 切片测试，对 Mapper 层写 `@MybatisTest`
- **面试考点**：测试金字塔、Mock vs Stub、TDD、覆盖率指标、Spring Boot Test 自动配置原理

### 4. 配置中心化
- **内容**：将 application.properties 补齐为完整配置（DB、Redis、OSS），拆分为 `application-dev.yml` / `application-prod.yml`，敏感信息用环境变量或 Jasypt 加密
- **面试考点**：Spring 配置优先级、`@ConfigurationProperties` vs `@Value`、敏感信息管理、12-Factor App

### 5. 全局异常处理完善
- **内容**：定义业务异常枚举 `ErrorCode`，GlobalExceptionHandler 按异常类型分级处理（参数校验 → 400、业务异常 → 自定义 code、未知异常 → 500 + 隐藏内部信息）
- **面试考点**：`@RestControllerAdvice` 原理、`ResponseEntityExceptionHandler`、统一返回体设计

---

## 🚀 进阶拓展（亮点加分）

### 6. 分布式会话 & OAuth2 第三方登录
- **内容**：Spring Security + OAuth2 Client 接入 GitHub/微信扫码登录；token 改为双 token 机制（access_token 30min + refresh_token 7d）
- **面试考点**：OAuth2 授权码流程、JWT vs Opaque Token、Spring Security 过滤器链、CSRF/XSS 防护

### 7. 消息队列异步解耦
- **内容**：引入 RabbitMQ（或 Kafka），将点赞通知、评论通知、操作日志写入、浏览量同步等从同步改为异步消息驱动
- **面试考点**：MQ 选型（RabbitMQ vs Kafka vs RocketMQ）、消息可靠性（confirm 机制、死信队列、幂等消费）、最终一致性、削峰填谷

### 8. 全文搜索 (Elasticsearch)
- **内容**：用 ES 替代 MySQL LIKE 做文章搜索；通过 Canal 或 MQ 监听 MySQL binlog 同步数据到 ES
- **面试考点**：倒排索引原理、ES 集群架构（分片/副本）、IK 分词器、搜索排序 (BM25)、MySQL 与 ES 数据同步方案对比

### 9. 分布式定时任务 (XXL-JOB)
- **内容**：引入 XXL-JOB，将缓存预热、浏览量批量同步、过期数据清理等改为分布式定时任务调度
- **面试考点**：分布式任务调度原理（抢占式 vs 分片式）、cron 表达式、失败重试 & 故障转移、XXL-JOB 架构（调度中心 vs 执行器）

### 10. 微服务拆分 & Spring Cloud
- **内容**：将用户服务、文章服务、评论服务拆分为独立微服务，引入 Nacos（注册中心 + 配置中心）、OpenFeign（远程调用）、Gateway（网关）、Sentinel（限流熔断）
- **面试考点**：微服务架构设计原则、服务发现 & 注册、远程调用协议 (HTTP vs gRPC)、分布式事务 (Seata)、链路追踪 (SkyWalking/Micrometer Tracing)

### 11. Docker 容器化 & CI/CD
- **内容**：写 Dockerfile + docker-compose.yml（MySQL + Redis + App），GitHub Actions 实现 push → test → build image → deploy
- **面试考点**：Docker 分层构建优化、Docker Compose 编排、CI/CD 流水线设计、蓝绿部署 vs 滚动更新

### 12. 系统可观测性
- **内容**：引入 Micrometer + Prometheus + Grafana 做指标监控（QPS、RT、缓存命中率、JVM 指标）；用 AOP 记录慢接口（>500ms）自动告警
- **面试考点**：Metrics vs Logging vs Tracing、Prometheus 数据模型、Grafana Dashboard 设计、SLI/SLO/SLA 概念

### 13. WebSocket 实时通知
- **内容**：用户收到新评论/点赞时 WebSocket 推送实时通知（右上角小红点 + 通知列表）
- **面试考点**：WebSocket vs SSE vs 轮询、STOMP 协议、Spring WebSocket 原理、在线用户状态管理、分布式 WebSocket（广播方案）

### 14. 压测 & 性能优化
- **内容**：用 JMeter 对核心接口（文章详情、首页列表、点赞）做压测，找到瓶颈；优化 SQL 加索引、加连接池监控、Redis  Pipeline 批量操作
- **面试考点**：性能调优方法论、慢 SQL 分析 (EXPLAIN)、连接池参数调优、JVM GC 调优、缓存策略决策树

### 15. DDD 领域驱动设计重构
- **内容**：按领域拆分 package（user、article、comment、like），每个领域内分 controller/service/repository/domain；引入领域事件（如 `ArticleLikedEvent`）、聚合根设计
- **面试考点**：DDD 核心概念（实体/值对象/聚合/领域服务/仓储）、六边形架构、CQRS 读写分离、事件驱动架构
