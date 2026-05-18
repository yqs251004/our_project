# API Message 后端迁移指南

## 目标

后端在保留现有 REST 路由的同时，逐步增加 message 风格入口，让前端可以通过浏览器路径访问：

```text
/api/authLoginApiMessage
```

当前前端 Vite 代理会把 `/api` 前缀转发到后端时剥掉，所以开发环境下后端实际收到的是：

```text
/authLoginApiMessage
```

如果生产环境网关不剥 `/api`，后端需要额外兼容 `/api/<messageName>`，或者由网关统一改写。

## 总体原则

- message endpoint 是前端契约入口，不替代 application service。
- 现有 REST router 先保留，message router 作为兼容层逐步接入。
- message 名称和前端函数名一致，例如 `authLoginApiMessage`。
- 所有 message 使用 `POST`。
- 所有输入都从 JSON body 读取，即使是查询接口也使用 input object。
- 路径参数和 query 参数变成 request case class 字段。

## 推荐目录

新增共享 message 基础设施：

```text
src/main/scala/riichinexus/api/http/
  ApiMessageRouter.scala
  ApiMessageRegistry.scala
  ApiMessageSupport.scala
```

服务自己的 message 定义放在对应微服务下：

```text
src/main/scala/riichinexus/microservices/auth/api/messages/
  AuthApiMessages.scala

src/main/scala/riichinexus/microservices/player/api/messages/
  PlayerApiMessages.scala
```

如果后续做代码生成，可以把可导出的契约元数据集中到 shared API 层。

## Message Handler 形态

建议每个 message handler 都是 `RouteSupport => Request[IO] => IO[Response[IO]]`，这样可以复用现有的 JSON、鉴权、错误处理工具。

示意结构：

```scala
final case class ApiMessageHandler(
    name: String,
    handle: (RouteSupport, Request[IO]) => IO[Response[IO]]
)
```

注册表按名称 dispatch：

```scala
object ApiMessageRegistry:
  def handlers: Map[String, ApiMessageHandler] =
    Seq(
      AuthApiMessages.authLoginApiMessage,
      AuthApiMessages.authRegisterApiMessage,
      AuthApiMessages.authRestoreSessionApiMessage
    ).map(handler => handler.name -> handler).toMap
```

router 负责兜底 unknown message：

```scala
object ApiMessageRouter:
  def routes(support: RouteSupport): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / messageName if messageName.endsWith("ApiMessage") =>
        ApiMessageRegistry.handlers.get(messageName) match
          case Some(handler) => support.handled(handler.handle(support, req))
          case None =>
            support.jsonResponse(
              Status.NotFound,
              ErrorResponse(s"Unknown API message: $messageName", code = "api_message_not_found")
            )
    }
```

如果需要兼容未剥前缀的生产路径，可以同时匹配：

```scala
case req @ POST -> Root / "api" / messageName if messageName.endsWith("ApiMessage") =>
```

## Auth 示例

现有 REST：

```scala
case req @ POST -> Root / "auth" / "login" =>
  support.handled {
    support.readJsonBody[LoginRequest](req).flatMap { request =>
      support.jsonResponse(Status.Ok, AccountApi.login(deps.authService, request, Instant.now()))
    }
  }
```

message handler：

```scala
object AuthApiMessages:
  val authLoginApiMessage: ApiMessageHandler =
    ApiMessageHandler(
      name = "authLoginApiMessage",
      handle = (support, req) =>
        val module = support.authModule
        support.readJsonBody[LoginRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            AccountApi.login(module.authService, request, Instant.now())
          )
        }
    )
```

REST 和 message 在过渡期调用同一个 `AccountApi.login`，避免两套业务逻辑。

## 输入建模

原来在路径或 query 里的字段进入 request case class：

```scala
final case class PlayerGetProfileApiMessageInput(
    playerId: String
) derives CanEqual

final case class ClubListApiMessageInput(
    keyword: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual
```

后端可以选择复用已有 request/query case class，但对前端暴露的 message contract 必须稳定。如果内部 query 模型频繁变化，建议单独定义 `*ApiMessageInput`，再映射到内部 query。

## 错误响应

继续复用 `HttpResponseSupport.errorResponse`：

- `IllegalArgumentException` -> 400
- `AuthenticationFailure` -> 401
- `AuthorizationFailure` -> 403
- `NoSuchElementException` -> 404
- `OptimisticConcurrencyException` -> 409

unknown message 使用：

```text
404 api_message_not_found
```

不要让 unknown message 变成 500。

## 接入 ApiRouter

在 `riichinexus/api/http/ApiRouter.scala` 里把 message router 放在具体 REST router 前面或后面都可以。建议放在 REST router 前面，message 名称有 `ApiMessage` 后缀，不会和现有资源路径冲突：

```scala
ApiMessageRouter.routes(support) <+>
  AuthMicroserviceRouter.authRoutes(support) <+>
  ...
```

## 推荐迁移顺序

1. `auth`
2. `player`
3. `publicquery`
4. `club`
5. `opsanalytics`
6. `platformadmin`
7. `tournament/appeal`
8. `tournament`
9. `dictionary`

每迁一个服务，先做 2 到 4 个核心 message，跑通前后端，再补齐该服务剩余接口。

## 后端验收标准

```powershell
sbt compile
sbt test
```

接口检查：

- 新 message router 能返回成功响应。
- unknown message 返回 404 和 `api_message_not_found`。
- REST 旧路由仍可用，直到前端完全迁完。
- message handler 没有复制业务逻辑，只复用现有 API/application service。
- request/response codec 明确 import，不依赖偶然的通配符顺序。

## 什么时候可以删除 REST 路由

满足以下条件后再删：

- 前端 `src/api/<service>` 已全部迁到 message 调用。
- 对接文档里的 message 清单已覆盖该服务所有当前使用接口。
- 前后端测试都通过。
- 手动验证核心流程通过。
- 没有外部客户端还依赖旧 REST 路径。

