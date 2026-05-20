# API Class 前后端对接清单

本清单是前后端共用文档。前端副本在 `front/doc/api-message-integration-checklist.md`，后端副本在 `our_project/docs/api-message-integration-checklist.md`。

## 对接目标

前端代码：

```ts
sendAPI(new LoginAuthAPI(username, password))
```

浏览器访问：

```text
/api/loginauthapi
```

后端注册并执行同一 API 概念的 class：

```scala
final case class LoginAuthAPIMessage(...) extends APIMessage[AuthResponse]
```

## 单个 API 的完成定义

一个 API 只有同时满足下面条件，才算对接完成：

- 前端有 `XxxAPI` class，例如 `LoginAuthAPI`。
- 后端有同名概念的 `XxxAPIMessage` class，例如 `LoginAuthAPIMessage`。
- 前端和后端 class 名能推导到同一个 path，例如 `/api/loginauthapi`。
- 前端每个 API 单独一个文件，文件名和 class 名一致。
- 后端每个 API 单独一个文件，文件名和 class 名一致。
- 前端 input/output 类型继续使用 `src/objects/<service>`。
- 前端 `src/apis/<service>` 只包含 `XxxAPI.ts`，不包含 `index.ts`、transport、类型文件或聚合 service。
- 后端 `<service>/api` 只包含 `XxxAPIMessage.scala`，不包含 registry、service、request/response 类型、旧 message/planner 或空目录。
- 后端 request/response/API contract 类型放在 `<service>/objects/apiTypes`。
- 后端 `XxxAPIMessage` 的字段、可选字段、枚举字符串与前端 `XxxAPI` 一致。
- 后端 `XxxAPIMessage.plan(...): IO[Output]` 能执行该 API。
- 后端服务级 registry/routes 注册了该 `XxxAPIMessage`。
- 前端通过 `sendAPI(new XxxAPI(...))` 调用，不手写 REST path。
- 成功响应和错误响应都能被前端现有错误处理处理。
- 前端 `npm.cmd run build` 通过。
- 后端 `sbt compile` 通过。

## API 清单格式

每迁移一个接口，在对应服务文档或迁移 PR 中补一行：

```text
frontend class | backend class | path | input fields | output | owner service | old REST route | status
```

示例：

```text
LoginAuthAPI | LoginAuthAPIMessage | /api/loginauthapi | username,password | AuthResponse | auth | POST /auth/login | done
RegisterAuthAPI | RegisterAuthAPIMessage | /api/registerauthapi | username,password,displayName | AuthResponse | auth | POST /auth/register | done
RestoreAuthSessionAPI | RestoreAuthSessionAPIMessage | /api/restoreauthsessionapi | bearer token | AuthSessionOutput | auth | GET /auth/session | done
LogoutAuthAPI | LogoutAuthAPIMessage | /api/logoutauthapi | bearer token | ApiMessage | auth | POST /auth/logout | done
CurrentSessionAuthAPI | CurrentSessionAuthAPIMessage | /api/currentsessionauthapi | operatorId,guestSessionId | CurrentSessionOutput | auth | GET /session | done
CreateGuestSessionAuthAPI | CreateGuestSessionAuthAPIMessage | /api/createguestsessionauthapi | displayName,ttlHours,deviceFingerprint | GuestSession | auth | POST /guest-sessions | done
GetGuestSessionAuthAPI | GetGuestSessionAuthAPIMessage | /api/getguestsessionauthapi | sessionId | GuestSession | auth | GET /guest-sessions/{sessionId} | done
RevokeGuestSessionAuthAPI | RevokeGuestSessionAuthAPIMessage | /api/revokeguestsessionauthapi | sessionId,reason | GuestSession | auth | POST /guest-sessions/{sessionId}/revoke | done
UpgradeGuestSessionAuthAPI | UpgradeGuestSessionAuthAPIMessage | /api/upgradeguestsessionauthapi | sessionId,playerId | GuestSession | auth | POST /guest-sessions/{sessionId}/upgrade | done
CreatePlayerAPI | CreatePlayerAPIMessage | /api/createplayerapi | userId,nickname,rankPlatform,tier,stars,initialElo | PlayerResponse | player | POST /players | done
GetCurrentPlayerAPI | GetCurrentPlayerAPIMessage | /api/getcurrentplayerapi | operatorId | PlayerResponse | player | GET /players/me | done
GetPlayerAPI | GetPlayerAPIMessage | /api/getplayerapi | playerId | PlayerResponse | player | GET /players/{playerId} | done
ListPlayersAPI | ListPlayersAPIMessage | /api/listplayersapi | clubId,status,nickname,limit,offset | PagedResponse[PlayerResponse] | player | GET /players | done
ListPublicSchedulesAPI | ListPublicSchedulesAPIMessage | /api/listpublicschedulesapi | tournamentStatus,stageStatus,limit,offset | PagedResponse[PublicScheduleView] | publicquery | GET /public/schedules | done
ListPublicTournamentsAPI | ListPublicTournamentsAPIMessage | /api/listpublictournamentsapi | status,organizer,limit,offset | PagedResponse[PublicTournamentSummaryView] | publicquery | GET /public/tournaments | done
GetPublicTournamentAPI | GetPublicTournamentAPIMessage | /api/getpublictournamentapi | tournamentId | PublicTournamentDetailView | publicquery | GET /public/tournaments/{tournamentId} | done
ListPublicClubsAPI | ListPublicClubsAPIMessage | /api/listpublicclubsapi | name,relation,limit,offset | PagedResponse[PublicClubDirectoryEntry] | publicquery | GET /public/clubs | done
GetPublicClubAPI | GetPublicClubAPIMessage | /api/getpublicclubapi | clubId | PublicClubDetailView | publicquery | GET /public/clubs/{clubId} | done
PublicPlayerLeaderboardAPI | PublicPlayerLeaderboardAPIMessage | /api/publicplayerleaderboardapi | clubId,status,limit,offset | PagedResponse[PlayerLeaderboardEntry] | publicquery | GET /public/leaderboards/players | done
PublicClubLeaderboardAPI | PublicClubLeaderboardAPIMessage | /api/publicclubleaderboardapi | name,limit,offset | PagedResponse[ClubLeaderboardEntry] | publicquery | GET /public/leaderboards/clubs | done
```

## 字段对齐规则

- 路径参数进入 class 字段，例如 `playerId`。
- query 参数进入 class 字段，例如 `limit`、`offset`、`keyword`。
- body 参数保持在 class 顶层，除非已有稳定嵌套结构。
- `operatorId`、`guestSessionId` 这类上下文参数显式放入字段，除非它来自 token。
- 空输入使用无字段 class。
- 日期时间统一使用 ISO string。
- enum 使用前端和后端共同确认的字符串值。

## Header 和鉴权

- 登录、注册等公开 API 继承 `APIMessage<Response>`。
- 需要登录态的 API 继承 `APIWithTokenMessage<Response>`。
- 前端 `sendAPI` 负责把 token 注入请求体或 header。
- 后端 router/context 负责把 token 解析成后端身份字段。
- 如果旧接口通过 query 传 `operatorId`，迁移时先保持 class 字段，后续再单独收敛到 token 身份。

## 路由前缀约定

前端：

```text
sendAPI(new LoginAuthAPI(...))
=> /api/loginauthapi
```

后端：

```text
LoginAuthAPIMessage
=> LoginAuthAPI
=> loginauthapi
```

开发代理如果剥 `/api`，后端过渡期可以同时接：

```text
POST /api/loginauthapi
POST /loginauthapi
```

长期应固定部署路径，不要让前端针对不同环境改 API class 名。

## 推荐纵向切片

第一批只做 `auth`：

```text
LoginAuthAPI / LoginAuthAPIMessage
RegisterAuthAPI / RegisterAuthAPIMessage
RestoreAuthSessionAPI / RestoreAuthSessionAPIMessage
LogoutAuthAPI / LogoutAuthAPIMessage
CurrentSessionAuthAPI / CurrentSessionAuthAPIMessage
CreateGuestSessionAuthAPI / CreateGuestSessionAuthAPIMessage
GetGuestSessionAuthAPI / GetGuestSessionAuthAPIMessage
RevokeGuestSessionAuthAPI / RevokeGuestSessionAuthAPIMessage
UpgradeGuestSessionAuthAPI / UpgradeGuestSessionAuthAPIMessage
```

完成后再进入 `player`。不要在 `auth` 未验证前直接迁 `tournament`。

## 联调步骤

1. 后端新增 `XxxAPIMessage.scala`；迁移中可短暂保留旧 REST route，完成态必须删除旧 REST route 和对应 `*MicroserviceRouter`。
2. 后端把 `XxxAPIMessage` 加入服务级 API 注册列表。
3. 后端运行：

   ```powershell
   sbt compile
   sbt test
   ```

4. 前端新增对应 `XxxAPI.ts`。
5. 前端使用 `sendAPI(new XxxAPI(...))` 替换旧 REST 调用。
6. 前端运行：

   ```powershell
   npm.cmd run build
   npm.cmd run test
   npm.cmd run lint
   ```

7. 手动请求一个成功样例和一个失败样例。
8. 在浏览器里跑对应业务流程。
9. 用搜索确认前端 API class 文件不再手写旧 REST path。

## 常用检查命令

前端：

```powershell
rg "request<|sendJson<|callApiMessage|xxxApiMessage|\"/" front/src/apis/auth
npm.cmd run build
```

后端：

```powershell
rg "LoginAuthAPIMessage|APIMessage\\[" our_project/src/main/scala
sbt compile
```

## 必做项

- 前端 `XxxAPI` 与后端 `XxxAPIMessage` 必须一一对应。
- 每个 API 必须单独文件、单独 class。
- 前端服务级 `src/apis/<service>` 目录只能放 `XxxAPI.ts`。
- 后端服务级 `<service>/api` 目录只能放 `XxxAPIMessage.scala`。
- 后端 registry/routes 必须在 `<service>/router`，request/response 类型必须在 `<service>/objects/apiTypes`；与 API 一一对应的 service/application wrapper 必须删除，业务编排进入 `XxxAPIMessage.plan`。
- 空的旧 `messages`、`requests`、`responses` 目录必须删除。
- API 文件里不保留仅用于 codec 的伴生对象；使用 `derives` 或集中 codec。
- path 必须由 class 名推导，不允许业务代码手写 REST path。
- 后端 router 必须短，只做查找、decode、执行、encode。
- 后端 `plan` 必须返回 `IO[Output]`。
- 第一阶段允许 `plan` 委托旧 service/API；最终完成时，应把业务编排移动到对应 `XxxAPIMessage` 文件。
- 已迁移完成的微服务必须删除旧 REST `*MicroserviceRouter` 兼容入口，并从总路由 `ApiRouter` 摘掉；完成态只允许 `/api/<class-derived-name>` 作为该微服务入口。

## shared 目录说明

- `microservices/shared` 不是业务微服务，不按 API class 迁移顺序处理。
- `shared/api/responses` 里的 `PagedResponse`、`ErrorResponse`、`HealthResponse` 是跨模块公共 response contract，可继续被业务 API class 复用。
- `shared/api/requests/OperatorRequest` 是旧接口风格的共享 request。后续迁移各业务模块时，应把这类字段逐步展开进具体 `XxxAPIMessage`，避免继续扩大共享 request。
- `shared/api/docs` 是 OpenAPI/Swagger 文档基础设施。业务模块迁移完成后，单独迁出 `microservices` 命名空间，目标可为 `riichinexus.api.docs`。
- 主迁移完成后，再统一评估 `shared/api/responses` 是否迁到 `riichinexus.api.responses` 或 `riichinexus.api.contracts.responses`。

## 风险点

- class 名一旦改动会改变 path，必须前后端同步。
- GET 迁成 POST 后，浏览器缓存语义会变化；当前业务后台型接口可以接受，公开列表如需缓存再单独设计。
- REST 删除要晚于前后端 API class 全量迁移，避免手动测试漏掉旧入口依赖。

## 阶段性验收

第一阶段完成标准：

- `auth` 全部 API class 化。
- 前端 auth API class 文件不再出现 `/auth`、`/session`、`/guest-sessions`。
- 前端 auth 每个 API 单独一个 class 文件，`front/src/apis/auth` 下没有 `index.ts` 或聚合文件。
- 后端 auth 每个 API 单独一个 `XxxAPIMessage.scala` 文件。
- 后端 `auth/api` 下只剩 `XxxAuthAPIMessage.scala` 文件。
- 后端 auth API registry 在 `auth/router`，只列 class，不放业务执行逻辑。
- 后端 auth request/response contract 在 `auth/objects/apiTypes`。
- 后端 auth 不保留 `auth/service` 目录或 `AuthApplicationService` / `GuestSessionApplicationService`。
- `AuthModuleAssembly.scala` 属于模块装配层，后续单独拆解或迁出到统一 bootstrap/module composition 位置；当前不作为 APIMessage 对接阻塞项。
- 前后端编译通过。
- 登录、注册、恢复会话、游客会话核心流程手测通过。
