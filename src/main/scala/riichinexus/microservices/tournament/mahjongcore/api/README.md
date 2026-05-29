# api 目录规范

`api` 只放 APIMessage。每个 API 一个 `XxxAPIMessage.scala` 文件，文件名、case class 名、前端 API 名保持一一对应。

## APIMessage 的职责

APIMessage 只做边界编排：

1. 解析 request 中的 id、enum、时间、分页等输入。
2. 解析 actor/operator/session。
3. 补充服务端生成字段，例如 `createdAt`、`calculatedAt`。
4. 构造 domain command/query。
5. 在 `IO.blocking` 中开启事务或调用同步 domain service/coordinator。
6. 把 domain 结果转换成 API view/response。

## plan 写法

- `plan` 应该是短的样板式主流程，用 `for` 串起步骤。
- 解析输入、查表、组装 view 可以拆成 private 方法，但 private 方法不能变成隐藏业务主流程的大杂烩。
- 如果 private 方法数量明显膨胀，优先判断逻辑是否应该进入 `domain`。
- 所有 JDBC、transaction manager、table 调用、同步 domain service 调用，都用 `IO.blocking` 包起来。

## 不应该放在 api 的内容

- 麻将规则、番种算法、分数计算。
- 对局状态机主流程。
- 牌谱解析细节。
- 数据库 SQL 与行映射。
- API DTO 以外的持久化模型。
- 为了转发而存在的 support/builder/helper 文件。

## 与 tables 的关系

API 可以直接调用 table object 来完成简单查询，不需要包一层没有业务意义的 `findXxx` 转发函数。只有当查询参数解析、权限校验、领域规则或结果组装明显复杂时，才把流程放进 domain service/coordinator。
