# api 目录规范

`api` 只放 `APIMessage`。每个 API 一个 `XxxAPIMessage.scala` 文件，文件名、case class 名、注册名保持一一对应。

## APIMessage 的职责

APIMessage 负责边界编排：

1. 解析 request 中的 id、enum、时间、分页等输入。
2. 解析 actor、operator、session。
3. 补充服务端生成字段，例如 id、createdAt、calculatedAt。
4. 构造 domain command/query。
5. 在 `IO.blocking` 中执行 JDBC、事务或同步领域流程。
6. 调用本子微服务 tables 或 domain functions。
7. 把 domain 结果转成 API view/response。

## plan 写法

- `plan` 是主链路，用 `for` 串起步骤。
- 不要用一个大的 `IO.blocking` 把所有逻辑包住后在里面写完整 API。
- 不要把主链路藏进一堆私有 assembler/helper。
- 私有方法可以用于小的解析和 view 组装，但不能承载隐藏业务流程。
- 如果 private 方法明显变多，优先判断是否应该拆到 `domain/<semantic>/functions`。

## 和 tables 的关系

API 可以直接调用本子微服务的 table 函数完成简单读写。table 函数是 `private[mahjongcore]`，所以只有 `mahjongcore` 内部 API/domain 能调用。

其它微服务不应该越过 API 直接读写 `mahjongcore.tables`。需要数据时新增或复用 private API。

## 不应该放在 api 的内容

- 麻将规则算法。
- 牌局状态机核心逻辑。
- SQL 和 ResultSet 映射。
- 可复用的领域函数。
- 无语义的 support/builder/assembler 文件。
