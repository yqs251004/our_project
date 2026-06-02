# mahjongcore 子微服务规范

`mahjongcore` 是 `tournament` 微服务下的麻将对局计算子微服务。它负责和麻将核心规则直接相关的后端能力，例如牌谱解析、牌局状态推进、局结果计算、分数变化、和种识别、对局归档前校验等。

当前目录主要是结构规范。后续新增代码时，应贴近现版本后端的重构方向：类型和纯函数分开，前端镜像类型和后端领域类型分开，按业务语义拆目录，跨微服务协作走 API。

## 总体边界

- `objects/` 放前端需要镜像的类型，以及 API request、query、response、view、对外 enum。
- `domain/` 放只有后端自己使用的领域类型、内部状态、领域异常、纯函数和领域流程。
- `api/` 放 `APIMessage`，每个 API 一个文件，主链路写在 `plan` 中。
- `tables/` 放 SQL/JDBC 表访问代码，每个 SQL 语句对应一个函数。
- `router/` 放 API 注册入口，只登记 API，不写业务逻辑。

## 语义目录

类型不要堆在根目录下。按照业务语义拆分，例如：

- `roundmanagement`
- `scoremanagement`
- `paifumanagement`
- `handanalysis`
- `yakuanalysis`
- `gamestate`
- `settlementmanagement`

同一个语义下面可以同时有：

- `objects/<semantic>/...`：前端需要镜像的类型。
- `domain/<semantic>/model/...`：后端内部领域类型。
- `domain/<semantic>/functions/...`：围绕该语义的纯函数。
- `tables/<table>/...`：该语义对应的持久化表。

## 依赖方向

允许：

- `api -> objects`
- `api -> domain`
- `api -> tables`
- `domain -> tables`，仅限后端领域流程确实需要读写本服务表。
- `objects -> domain`，仅限 `fromDomain` / `toDomain` 这类边界转换。
- `router -> api`

禁止：

- `domain -> api`
- `domain -> objects.apiTypes`
- `tables -> api`
- `tables -> objects.apiTypes`
- `objects -> tables`
- 其它微服务直接调用 `mahjongcore.tables`

其它微服务如果需要读取或更新 `mahjongcore` 数据，必须调用 `mahjongcore` 暴露的 public/private API。

## 类型和函数

- case class、enum、value object 只表达数据结构，不挂业务方法。
- 领域模型的方法拆到 `domain/<semantic>/functions`。
- API view/request 到 domain 的转换，如果只有单个 API 使用，收到 API 文件内；如果多个 API 复用，可以挂在对应 `objects` 类型的 companion 上，统一命名 `fromDomain` / `toDomain`。
- enum 只保留 `toString` / `fromString` 这类边界形式，不在 enum 内混入业务判断。
- ID 类型如果前端需要镜像，放 `objects/<semantic>`；ID 生成器属于后端函数，放 `domain/functions` 或对应语义的 `domain/<semantic>/functions`。

## Tables 规则

- 一个 SQL 语句对应一个 table 函数。
- table 函数设为 `private[mahjongcore]`，避免跨微服务直接读表。
- table 函数只做 SQL、参数绑定、ResultSet 映射、乐观锁版本处理。
- 不在 tables 里开事务，不做权限校验，不组装 API view，不写麻将规则计算。
- 复杂流程放在 API plan 或 domain coordinator/function 中串起来。

## 命名

- API 文件：`XxxAPIMessage.scala`
- API 类型：`objects/<semantic>/apiTypes/XxxRequest.scala`、`XxxView.scala`、`XxxQuery.scala`
- 领域类型：`domain/<semantic>/model/Xxx.scala`
- 领域函数：`domain/<semantic>/functions/XxxFunctions.scala`
- Table 文件：`tables/<table>/XxxTable.scala`

不要新增 `package.scala` 或 package object。
