# domain 目录规范

`domain` 放麻将对局计算引擎的领域逻辑。这里是规则和流程的中心，不是 API DTO 的延伸。

## 应该放在 domain 的内容

- 对局计算 command/query。
- 牌谱解析、局结果推导、分数计算、番种判断。
- 对局状态机、结算流程、校验规则。
- coordinator/service，用于组织一段完整领域流程。
- domain model、domain enum、domain exception。

## 边界要求

- 不 import `api`。
- 不 import `objects.apiTypes.*`。
- 不知道 API request/response/view 的名字。
- 不返回前端专用 DTO。
- 不处理 HTTP、token、session、分页 envelope。

## service/coordinator 写法

- coordinator 负责领域主流程，适合放“计算一局并提交结果”“归档牌谱并生成 match record”这类用例。
- service 负责较小的领域能力，适合放“解析牌谱”“计算得分”“校验和牌形”等能力。
- 方法入参优先使用 domain command/query，不直接接收 API request。
- 如果方法需要事务，明确约定由调用方开启，或者由 service 自己通过 transaction manager 管理；不要两边都隐式开启。

## 与 tables 的关系

domain 可以直接调用 tables 执行必要的持久化读写，但不能把 table 调用伪装成一层无意义转发。需要读数据库时，直接调用对应 table object；需要业务流程时，流程留在 domain 方法里。
