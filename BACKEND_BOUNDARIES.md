# 后端结构边界说明

本文档记录当前后端微服务目录的职责边界。除 `system` 外，微服务代码应按这里的规则组织。

## 总体规则

- `api` 是前端可见 API。这里的 `*APIMessage` 是前后端通信入口，前端需要有同名 API wrapper。
- `api/private` 是后端内部 API。它只表达后端服务之间的协作语义，前端不需要镜像。
- `objects` 是前端需要镜像的公开类型。后端 public object 与前端 `front/src/objects` 应按路径、名称、字段保持一致。
- `objects/private` 是后端内部传输类型和 read model。它可以被其它后端微服务通过 private API 使用，前端不需要镜像。
- `domain` 是后端领域内部。它包含业务模型和纯规则，不直接作为跨微服务契约，也不要求前端感知。
- `tables` 只负责本微服务的数据读写。其它微服务不能直接读写表，必须通过 public API 或 private API。

## `domain`

`domain` 只对本微服务内部负责，表达业务概念、状态转换、校验规则和领域计算。

- `domain/model` 只放领域类型。
- `domain/functions` 放纯逻辑、规则计算、状态转换和领域 helper。
- `domain` 下的服务型对象可以编排本微服务内的领域逻辑，但不应承担 API 流程或表访问职责。
- 跨微服务时不直接引用其它服务的 `domain`。如需其它服务数据，应通过对方 API 获取 public object 或 `objects/private` read model。

## `api/private`

`api/private` 是后端内部协作入口，适合表达“后端系统动作”，而不是前端用户操作。

- 名称应说明为什么它不是前端语义，例如 `Record...PrivateAPIMessage`、`Resolve...PrivateAPIMessage`、`Refresh...PrivateAPIMessage`。
- 返回值优先使用本服务的 `objects/private` read model，避免泄露 `domain` 类型。
- 可以串联其它微服务的 API，但主流程应清楚，plan 中能看出该 private API 做了什么。
- 不与 public API 重复；如果 private 与 public 语义重合，优先保留 public API。

## `api`

`api` 是前端可调用入口，文件名与前端 `front/src/api` 一一对应。

- 每个 public API 文件开头应说明该 API 的功能。
- `plan` 应呈现主流程：解析输入、校验权限、读取/写入、调用其它 API、返回 view。
- 表读写可以由 API 执行，但大块副作用应包在 `IO` 中，并拆成命名清楚的小步骤。
- API 文件不内联公开 request/response/view 类型；这些类型应放在 `objects/apiTypes`。

## `objects`

`objects` 是公开 contract 层。前端必须有对应类型。

- 类型路径、文件名、类型名和字段应与前端保持一致。
- 公开 request/query/response/view 放在各模块的 `apiTypes` 下。
- 稳定 ID、枚举和值对象放在对应业务目录下。
- 如果某个类型只用于后端内部 private API，不应放在 public `objects`。

## `objects/private`

`objects/private` 是后端内部 contract 层。

- 适合 private API 的 read model、draft、内部 request/response。
- 可跨微服务使用，但它仍是后端内部契约，前端不镜像。
- 它应比 `domain` 更稳定、更窄，只暴露调用方需要的字段。
- 如果一个 private object 与 public object 字段和语义完全相同，优先复用 public object，不重复定义。

## `tables`

`tables` 是本微服务的持久化边界。

- 只负责 SQL、行映射、保存、查询、分页等数据访问。
- 不放跨微服务调用。
- 不承载领域决策；复杂业务规则应在 `domain/functions` 或 API 流程中表达。
- 其它微服务需要表中数据时，由本服务通过 API 或 private API 提供。

## 当前对齐状态

- public API：后端 120 个，前端 120 个，路径与名称已对齐。
- public objects：后端 233 个，前端 233 个，路径与名称已对齐。
- 可解析的 public case class/interface 字段对比无差异。
