# router 目录规范

`router` 放 `mahjongcore` 的 APIMessage 注册入口。router 只声明哪些 API 对外或对内开放，不写业务流程。

## 应该放在 router 的内容

- APIMessage registry。
- API 名称。
- success status。
- token requirement。
- public/private API 的注册分组。

## 禁止内容

- 不写麻将规则。
- 不查表。
- 不解析 request。
- 不构造 domain command。
- 不调用 tables。
- 不调用其它微服务。

新增 API 时，同步更新 registry。router 是“哪些 API 可以被调用”的唯一入口。
