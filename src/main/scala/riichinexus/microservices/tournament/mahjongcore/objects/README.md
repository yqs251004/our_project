# objects 目录规范

`objects` 放 API 边界对象和可被前端对齐的结构。这里描述“对外长什么样”，不描述“内部怎么计算”。

## 应该放在 objects 的内容

- API request、response、query、view。
- 前后端共享的 API enum。
- API view 到 domain model 的边界转换。
- 小型展示结构，例如对局摘要、得分明细、牌谱视图。

## 文件组织

- 一个 object/type 一个文件。
- API 专用类型可放在 `apiTypes` 子目录；若未来建立该子目录，也要保持一个类型一个文件。
- enum 如果会被前端使用，应放在 objects 边界，而不是 domain 私有 enum。

## 转换规则

- `fromDomain`：domain model 转 API view。
- `toDomain`：API request/enum 转 domain command/domain enum。
- 转换只做字段映射、边界默认值、API enum/domain enum 对齐。
- 不查数据库，不开启事务，不调用 table。

## 禁止内容

- 业务主流程。
- 麻将计算算法。
- SQL/JDBC。
- 权限校验。
- 依赖 APIMessage。
