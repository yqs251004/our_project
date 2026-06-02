# objects 目录规范

`objects` 放前端需要镜像的类型和 API 边界类型。这里描述“对外长什么样”，不承载内部麻将规则流程。

## 应该放在 objects 的内容

- API request、query、response、view。
- 前端需要使用的 enum 和 value object。
- 对外展示的牌谱摘要、局结果视图、得分明细、和种展示、对局状态视图。
- 前端需要镜像的 ID 类型。

## 语义组织

按业务语义建目录，例如：

- `objects/paifumanagement`
- `objects/roundmanagement`
- `objects/scoremanagement`
- `objects/handanalysis`
- `objects/yakuanalysis`
- `objects/gamestate`
- `objects/settlementmanagement`

API 专用类型放在对应语义下的 `apiTypes`，不要全部堆在 `objects/apiTypes`。

## 转换规则

- 多个 API 复用的转换可以放在 companion 上，统一叫 `fromDomain` / `toDomain`。
- 只有一个 API 使用的转换，收到对应 API 文件里。
- 转换只做字段映射、默认值、enum/string 边界对齐。
- 转换不查表、不做权限校验、不调用 table、不执行麻将规则计算。

## 禁止内容

- SQL/JDBC。
- 事务。
- 权限校验。
- 麻将规则主流程。
- 牌局状态机。
- 只服务后端内部的 private domain type。
