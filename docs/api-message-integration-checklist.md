# API Message 前后端对接清单

本清单是前后端共用文档。前端副本在 `front/doc/api-message-integration-checklist.md`，后端副本在 `our_project/docs/api-message-integration-checklist.md`。

## 对接目标

前端代码：

```ts
xxxApiMessage(input)
```

浏览器访问：

```text
/api/xxxApiMessage
```

后端接收并分发到同名 message handler。当前开发环境中，Vite 会把 `/api` 前缀剥掉后转发到 `http://127.0.0.1:8080`，所以后端开发路由通常匹配 `/xxxApiMessage`。

## 单个 Message 的完成定义

一个 message 只有同时满足下面条件，才算对接完成：

- 前端有同名函数，例如 `authLoginApiMessage`。
- 前端函数通过统一 transport 推导路径，不手写 REST path。
- 前端 input/output 类型在 `src/objects/<service>`。
- 后端 registry 注册了同名 handler。
- 后端 handler 复用现有 application service 或 query API。
- 前后端字段名、可选字段、枚举字符串一致。
- 成功响应和错误响应都能被前端现有 `ApiError` 处理。
- 前端 `npm.cmd run build` 通过。
- 后端 `sbt compile` 通过。

## Message 清单格式

每迁移一个接口，在对应服务文档或迁移 PR 中补一行：

```text
messageName | frontend input | backend input | output | owner service | old REST route | status
```

示例：

```text
authLoginApiMessage | AuthLoginInput | LoginRequest | AuthSuccessOutput | auth | POST /auth/login | done
authRestoreSessionApiMessage | AuthRestoreSessionInput | token from Authorization | AuthSessionOutput | auth | GET /auth/session | planned
```

## 字段对齐规则

- 路径参数进入 input，例如 `{ playerId }`。
- query 参数进入 input，例如 `{ limit, offset, keyword }`。
- body 参数保持在 input 顶层，除非已有稳定嵌套结构。
- `operatorId`、`guestSessionId` 这类上下文参数显式放入 input，除非它来自 Authorization header。
- 空输入使用 `{}`。
- 日期时间统一使用 ISO string。
- enum 使用前端和后端共同确认的字符串值。

## Header 和鉴权

保留当前 header 语义：

- 登录、注册等公开 message 不要求 Authorization。
- 需要登录态的 message 继续使用 `Authorization: Bearer <token>`。
- 如果旧接口通过 query 传 `operatorId`，迁移时先保持 input 字段，后续再单独收敛到 token 身份。

## 路由前缀约定

前端：

```text
API_BASE_URL = /api
callApiMessage("authLoginApiMessage", input)
=> /api/authLoginApiMessage
```

开发代理：

```text
/api/authLoginApiMessage
=> http://127.0.0.1:8080/authLoginApiMessage
```

后端开发环境：

```text
POST /authLoginApiMessage
```

生产环境二选一：

- 网关继续剥 `/api`，后端只接 `/authLoginApiMessage`。
- 网关不剥 `/api`，后端同时接 `/api/authLoginApiMessage`。

这个选择必须在部署配置里固定，不要让前端针对不同环境改 message 名字。

## 推荐纵向切片

第一批只做 `auth`：

```text
authLoginApiMessage
authRegisterApiMessage
authRestoreSessionApiMessage
authLogoutApiMessage
authCurrentSessionApiMessage
authCreateGuestSessionApiMessage
authGetGuestSessionApiMessage
authRevokeGuestSessionApiMessage
authUpgradeGuestSessionApiMessage
```

完成后再进入 `player`。不要在 `auth` 未验证前直接迁 `tournament`。

## 联调步骤

1. 后端新增 message handler，并保留旧 REST route。
2. 后端运行：

   ```powershell
   sbt compile
   sbt test
   ```

3. 前端新增对应 registry 和 API function。
4. 前端运行：

   ```powershell
   npm.cmd run build
   npm.cmd run test
   npm.cmd run lint
   ```

5. 手动请求一个成功样例和一个失败样例。
6. 在浏览器里跑对应业务流程。
7. 用搜索确认前端服务模块不再手写旧 REST path。

## 常用检查命令

前端：

```powershell
rg "request<|sendJson<|\"/" front/src/api/auth
npm.cmd run build
```

后端：

```powershell
rg "authLoginApiMessage|ApiMessageRegistry|ApiMessageRouter" our_project/src/main/scala
sbt compile
```

## 风险点

- TypeScript 编译只能保证前端 registry 内部一致，不能天然保证后端已注册。
- 真正的跨端保证需要后端导出 message registry，前端由它生成类型或至少生成 message 名称清单。
- GET 迁成 POST 后，浏览器缓存语义会变化；当前业务后台型接口可以接受，公开列表如需缓存再单独设计。
- REST 删除要晚于前后端 message 全量迁移，避免手动测试漏掉旧入口依赖。

## 阶段性验收

第一阶段完成标准：

- `auth` 全部 message 化。
- 前端 auth API module 不再出现 `/auth`、`/session`、`/guest-sessions`。
- 后端 auth message handler 和旧 REST route 结果一致。
- 前后端编译通过。
- 登录、注册、恢复会话、游客会话核心流程手测通过。

