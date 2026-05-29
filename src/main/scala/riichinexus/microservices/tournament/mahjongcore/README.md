# mahjongcore 子微服务规范

`mahjongcore` 是 `tournament` 微服务下的赛事对局计算引擎子微服务。它的职责是承载对局计算、牌谱解析、局结果推导、分数与状态变更等与赛事对局核心规则相关的后端能力。

当前目录只建立结构与规范，不放具体实现。新增代码时保持现有后端风格：一个 API 一个文件，一个 object/api type 一个文件，API 只做请求到领域的边界转换，领域流程放在 domain，数据库访问放在 tables。

## 目录边界

- `api/`
  - 放 APIMessage。
  - APIMessage 负责解析 API request、解析 actor、构造 domain command/query、开启必要事务、调用 domain service/coordinator、把 domain 结果转成 API view。
  - 不写牌理、番种、结算、状态机等领域规则。
  - 不新增 support/builder 类来转发主流程。

- `domain/`
  - 放麻将计算引擎的领域模型、命令、服务、coordinator、规则算法与领域异常。
  - 不 import `objects.apiTypes.*`。
  - 不知道 API request/response 的名字。
  - 可以直接接收 domain command/query 和 repository/table 查询结果。

- `objects/`
  - 放 API 边界对象、view、request、response、query、API enum。
  - DTO 与 domain model 的转换方法也放这里，例如 `fromDomain` / `toDomain`。
  - 不写业务流程，不访问数据库。

- `router/`
  - 放该子微服务 APIMessage 的注册入口。
  - 只负责声明 API registry，保持名称、success status、token requirement 与 APIMessage 一一对应。

- `tables/`
  - 放 JDBC table mapper 与 SQL。
  - 每个数据库表一个子目录或一个明确的 table object。
  - table 方法只做 SQL、行映射、乐观锁版本处理。
  - 调用 tables 时直接调用对应 table object，不通过无意义的中间函数转发。

## 跨层依赖方向

允许方向：

- `api -> domain`
- `api -> objects`
- `api -> tables`
- `domain -> tables`，仅当领域服务确实需要读取/保存持久化数据
- `objects -> domain`，仅限边界转换
- `router -> api`

禁止方向：

- `domain -> api`
- `domain -> objects.apiTypes`
- `tables -> api/domain service/objects.apiTypes`
- `objects -> tables`
- `router -> domain/tables`

## IO 与事务

- API 中所有同步数据库、事务、JSON 编解码、大对象序列化都必须放在 `IO.blocking` 边界内。
- 普通 `IO(...)` / `IO { ... }` 不用于 JDBC、文件、网络、事务、同步仓储、重计算。
- 事务开启位置通常在 APIMessage 的 plan 中，或者由明确的 domain coordinator/service 管理；不要在 table 层开启事务。
- domain service 如果已经假设在事务内运行，命名和 README/注释中要说清楚。

## 命名

- API 文件：`XxxAPIMessage.scala`
- Request/query/view：放 `objects/apiTypes` 或 `objects` 下，按现有模块实际边界保持一致。
- Table 文件：`XxxTable.scala`
- Domain command：`XxxCommand.scala`
- Domain coordinator/service：`XxxCoordinator.scala` / `XxxService.scala`

Scala package 使用 `riichinexus.microservices.tournament.mahjongcore` 这一类无连字符路径，保持 IDE、编译器和目录结构一致。
