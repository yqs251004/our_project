# API Class 后端迁移指南

## 目标

后端从 REST router 和 `ApiMessageHandler` 聚合文件，迁移到每个 API 一个 class：

```scala
final case class LoginAuthAPIMessage(
    username: String,
    password: String
) extends APIMessage[AuthResponse]:
  override def plan(...): IO[AuthResponse] =
    ...
```

前端对应：

```ts
export class LoginAuthAPI extends APIMessage<AuthResponse> { ... }
```

浏览器访问：

```text
/api/loginauthapi
```

路径由 API class 名推导，不再维护 `authLoginApiMessage` 这类字符串 message name，也不再把多个 API 放进一个服务级 `AuthApiMessages.scala` 大文件。

## 总体原则

- 每个 API 一个后端 class，一个文件，文件名与 class 名一致。
- 后端 class 名以 `APIMessage` 结尾，例如 `LoginAuthAPIMessage`。
- 前端 class 名以 `API` 结尾，例如 `LoginAuthAPI`。
- `LoginAuthAPIMessage` 与 `LoginAuthAPI` 表示同一个 API 概念，路径统一推导为 `/api/loginauthapi`。
- 所有 API 使用 `POST`。
- 所有输入从 JSON body 反序列化为对应 `XxxAPIMessage` class。
- 路径参数和 query 参数变成 `XxxAPIMessage` 字段。
- API class 的 `plan(...): IO[Response]` 是该 API 的执行计划；不再额外区分 message 和 planner。
- 业务编排逐步收敛到对应 API class 文件中，旧 service/API wrapper 只作为过渡依赖。
- 服务模块的 `api` 目录必须保持样板形态：顶层只放 `XxxAPIMessage.scala`。
- 服务级 registry/routes 放到 `<service>/router`，不能放在 `<service>/api`。
- 不保留与 API 一一对应的 application/service wrapper；业务编排最终进入对应 `XxxAPIMessage.plan`。确实跨模块复用的底层组件要改成明确的 domain/operations/helper 命名，不能叫 `*ApplicationService` 来承载 API 编排。
- request/response/API contract 类型放到 `<service>/objects/apiTypes`，不能放在 `<service>/api/requests` 或 `<service>/api/responses`。
- 旧 `messages`、`requests`、`responses` 目录迁完必须删除；空目录不能留作占位。
- API 文件里不要保留仅用于 codec 的伴生对象；优先使用 `derives`，或把 codec 集中放到 `objects/apiTypes`。

## 推荐目录

共享 API 基础设施：

```text
src/main/scala/riichinexus/api/
  APIMessage.scala
  APIMessageRouter.scala
```

服务自己的 API class：

```text
src/main/scala/riichinexus/microservices/auth/api/
  LoginAuthAPIMessage.scala
  RegisterAuthAPIMessage.scala
  RestoreAuthSessionAPIMessage.scala

src/main/scala/riichinexus/microservices/player/api/
  CreatePlayerAPIMessage.scala
  GetPlayerProfileAPIMessage.scala
```

服务级 routes/registry 文件只负责列出支持的 API class，不放具体执行逻辑。

服务内非 API 文件应放到 API 目录之外：

```text
src/main/scala/riichinexus/microservices/auth/router/
  AuthAPIMessageRegistry.scala
  AuthMicroserviceRouter.scala

src/main/scala/riichinexus/microservices/auth/objects/apiTypes/
  AuthResponses.scala
  GuestSessionResponses.scala
  AccountRequests.scala

src/main/scala/riichinexus/microservices/auth/objects/
  GuestSessionOperations.scala
  AuthPasswordHasher.scala
```

装配层文件不属于服务样板目录。`src/main/scala/riichinexus/microservices/<service>/*ModuleAssembly.scala` 这类文件只是把 repositories、transaction manager、跨模块 service 组装成模块上下文。当前 `AuthModuleAssembly.scala` 先保留，后续应拆解或迁出到统一 bootstrap/module composition 位置，例如 `bootstrap/modules/AuthModuleAssembly.scala`，或并入 `ApplicationAssembly`。

## APIMessage 形态

共享 trait：

```scala
trait APIMessage[Response]:
  def plan(context: ApiPlanContext): IO[Response]
```

`ApiPlanContext` 可以包装当前项目已有的 `RouteSupport`、模块上下文、数据库连接、鉴权上下文等。核心要求是：HTTP `Request[IO]` 不进入每个业务 API class；router 负责反序列化，API class 只拿类型化输入和执行上下文。

需要登录态的 API 可以使用子 trait：

```scala
trait APIWithTokenMessage[Response] extends APIMessage[Response]
```

示例：

```scala
final case class LoginAuthAPIMessage(
    username: String,
    password: String
) extends APIMessage[AuthResponse]:

  override def plan(context: ApiPlanContext): IO[AuthResponse] =
    val cleanUsername = username.trim.toLowerCase
    val cleanPassword = password.trim
    // 业务编排写在当前文件中；可以调用 repository/table/domain helper。
    ???
```

## 注册与路由

注册表只列出支持哪些 API：

```scala
object AuthAPIRoutes:
  val apiMessages: List[RegisteredAPIMessage] = List(
    RegisteredAPIMessage.api[LoginAuthAPIMessage, AuthResponse],
    RegisteredAPIMessage.api[RegisterAuthAPIMessage, AuthResponse],
    RegisteredAPIMessage.apiWithToken[RestoreAuthSessionAPIMessage, AuthSessionResponse]
  )
```

后端通过 class 名推导 API path：

```scala
LoginAuthAPIMessage -> LoginAuthAPI -> loginauthapi
```

router 只做四件事：

1. 按 path 找 `RegisteredAPIMessage`。
2. 把 JSON body 反序列化为对应 `XxxAPIMessage`。
3. 执行 `message.plan(context)`。
4. 统一编码响应和错误。

开发代理如果仍会剥掉 `/api`，后端过渡期可以同时兼容：

```text
POST /api/loginauthapi
POST /loginauthapi
```

长期推荐固定为 `/api/<apiName>`，避免前端按环境改名。

## 输入建模

原来在路径或 query 里的字段进入 API class 字段：

```scala
final case class GetPlayerProfileAPIMessage(
    playerId: String
) extends APIMessage[PlayerProfileResponse]:
  override def plan(context: ApiPlanContext): IO[PlayerProfileResponse] =
    ???

final case class ListClubsAPIMessage(
    keyword: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[ClubListResponse]:
  override def plan(context: ApiPlanContext): IO[ClubListResponse] =
    ???
```

没有输入的接口使用无字段 case class 或 no-request 注册方式：

```scala
final case class LogoutAuthAPIMessage() extends APIWithTokenMessage[LogoutResponse]:
  override def plan(context: ApiPlanContext): IO[LogoutResponse] =
    ???
```

## 去 service 化

当前版本已经有很多 `*Api`、`*Service` 方法。按新模式迁移时分两步：

1. 第一阶段只改结构：每个 API 独立 class 文件，`plan` 暂时可以委托旧 service/API，保证行为不变。
2. 第二阶段逐个把旧 service/API 的业务编排移动到对应 `XxxAPIMessage.plan` 中，删除不再使用的 service/API wrapper。

完成后，`XxxAPIMessage` 可以直接调用 repository/table/domain helper，但不应只是薄薄转发到同名 service 方法。

## 错误响应

router 统一处理错误：

- 参数/JSON 解析错误 -> 400
- 未登录 -> 401
- 无权限 -> 403
- 找不到资源 -> 404
- 乐观锁/状态冲突 -> 409
- 未注册 API -> 404

不要让 unknown API 或 JSON decode 失败变成 500。

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

每迁一个服务，先做 2 到 4 个核心 API class，跑通前后端，再补齐该服务剩余接口。

## 后端验收标准

```powershell
sbt compile
sbt test
```

接口检查：

- 每个 API 一个 `XxxAPIMessage.scala` 文件。
- 服务 `api` 目录顶层只包含 `*APIMessage.scala`，没有 registry、service、request/response、旧 message/planner 或空目录。
- 不存在 `auth/service` 这类 API 编排 wrapper 目录。
- `AuthModuleAssembly.scala` 这类模块装配文件已登记为后续拆解/迁出目标，不作为 APIMessage 样板完成度的一部分。
- `XxxAPIMessage.scala` 文件中不保留仅提供 codec 的伴生对象。
- `XxxAPIMessage` 直接 extends `APIMessage[Response]` 或 `APIWithTokenMessage[Response]`。
- 服务级 registry/routes 只列支持的 API class，不承载业务执行逻辑。
- router 不包含业务分支，只负责查找、decode、执行、encode。
- path 由 class 名推导，不能手写旧 REST path 或 `xxxApiMessage` 字符串。
- unknown API 返回统一 404。
- 旧 REST 路由仍可用，直到前端完全迁完。
- 第一阶段允许 `plan` 委托旧 service/API；最终完成时，业务编排应收敛到对应 API class 文件中。

## 什么时候可以删除 REST 路由

满足以下条件后再删：

- 前端 `src/apis/<service>` 已全部迁到 API class 调用。
- 后端对应服务所有当前使用接口都有 `XxxAPIMessage` class 并已注册。
- 前后端 class 名一一对应，路径推导一致。
- 前后端测试都通过。
- 手动验证核心流程通过。
- 没有外部客户端还依赖旧 REST 路径。
