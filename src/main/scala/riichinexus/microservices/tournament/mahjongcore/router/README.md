# router 目录规范

`router` 放 `mahjongcore` 子微服务的 APIMessage 注册入口。

## 应该放在 router 的内容

- APIMessage registry。
- API 名称归一化所需的注册列表。
- 每个 API 的 success status。
- 是否需要 bearer token 的注册声明。

## 写法要求

- router 只依赖 `api` 层的 APIMessage。
- registry 中每个 APIMessage 与 `api/` 下文件一一对应。
- 不写业务逻辑。
- 不查数据库。
- 不解析 request。
- 不构造 domain command。

## 注册边界

新增 API 时，同步更新本目录 registry；不要把注册散落到 APIMessage 文件或 domain 文件中。router 是“哪些 API 对外开放”的唯一入口。
