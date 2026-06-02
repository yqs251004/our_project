# domain 目录规范

`domain` 放 `mahjongcore` 后端内部领域模型和领域函数。这里描述“后端怎么计算”，不描述“前端看到什么”。

## 应该放在 domain 的内容

- 后端内部对局状态、局内事件、结算上下文、牌型分析上下文。
- 只被后端使用的 domain enum、domain exception、domain command/query。
- 麻将规则计算、状态推进、牌谱解析、分数计算、和种判断。
- 领域流程函数或 coordinator，例如“归档牌谱并生成计算结果”。

## model 和 functions

- `model/` 只放数据类型，不在类型里挂业务方法。
- `functions/` 放围绕领域类型的纯函数。
- 按语义拆目录，不要把全部函数塞进一个宽泛的 `MahjongCoreFunctions.scala`。
- 函数命名要表达业务语义，例如 `HandAnalysisFunctions`、`RoundSettlementFunctions`、`GameStateTransitionFunctions`。

## 和 objects 的边界

- 前端需要镜像的类型放 `objects`，不要放 `domain`。
- 只有后端内部使用的类型放 `domain`。
- `domain` 不 import `objects.apiTypes.*`。
- `domain` 不返回 API view、response、paged response。
- `domain` 可以接收 API 转好的 command/query，但不知道 API request 的名字。

## 和 tables 的关系

domain 可以调用 `mahjongcore.tables`，但只限本子微服务内部流程。其它微服务不能直接调用这些 table 函数。

如果领域流程需要多步读写表，可以在 domain 函数或 coordinator 中组织流程，但 SQL 本身仍留在 tables 中。不要新增只做转发的 repository/helper。

## 事务和 IO

- 事务通常由 API plan 打开。
- 如果 domain 函数要求在事务内调用，要在函数命名或注释中说清楚。
- 纯计算函数不做 JDBC、文件、网络、时间读取、随机数生成。
- 需要生成 ID 或当前时间时，由 API 或专门后端函数传入结果。
