# tables 目录规范

`tables` 是 `mahjongcore` 的 SQL/JDBC 边界。它只负责本子微服务自己的表访问，不对其它微服务开放直接读写入口。

## 核心规则

- 一个数据库表一个子目录。
- 一个 SQL 语句对应一个函数。
- 每个 table 函数设为 `private[mahjongcore]`。
- 其它微服务如果要读取或更新 `mahjongcore` 数据，调用 `mahjongcore` 的 API，不直接调用 table。

## 函数粒度

推荐：

```scala
private[mahjongcore] def findById(connection: Connection, id: PaifuId): Option[PaifuRecord]
private[mahjongcore] def insert(connection: Connection, record: PaifuRecord): Unit
private[mahjongcore] def updateStatus(connection: Connection, id: PaifuId, status: PaifuStatus, expectedVersion: Long): Unit
```

不推荐：

```scala
def archivePaifuAndRecomputeEverything(...)
def findOrCreateAndPublishView(...)
def runMahjongSettlementWorkflow(...)
```

后一类是领域流程，应该放在 API plan 或 domain functions/coordinator 中。

## table 函数只做这些事

- SQL 字符串。
- PreparedStatement 参数绑定。
- ResultSet 到持久化类型的映射。
- insert/update/delete/select。
- 乐观锁版本检查和抛出系统级并发异常。

## table 函数不做这些事

- 不开事务。
- 不校验 token、session、permission。
- 不组装 API view。
- 不 import `api`。
- 不 import `objects.apiTypes`。
- 不执行麻将规则计算。
- 不调用其它微服务 API。

## 跨微服务边界

`private[mahjongcore]` 是强约束：tables 是本子微服务内部实现。其它微服务读取牌谱、对局结果、统计输入或状态时，应通过 `mahjongcore/api/public` 或 `mahjongcore/api/private` 暴露的 APIMessage 完成。
