# 后端微服务化重构概述

## 当前目标

当前后端仍然保持单一工程、单一进程、单一启动入口，不做独立部署式微服务拆分。

本轮重构的目标是先把单体后端整理成更清晰的模块边界：

- 业务代码按模块归入 `src/main/scala/riichinexus/microservices/*`。
- 各模块保留轻量结构：`api/`、`objects/`、`tables/`、`router/`。
- 跨模块调用优先通过模块自己的 `api/` 入口。
- `ApplicationAssembly` 继续作为中心 composition root 存在，但只负责拼装模块，不负责知道模块内部构造细节。
- 共享 infrastructure、domain、OpenAPI、JSON codec 和 HTTP runtime 仍然保留为公共支撑层。

## 当前模块边界

已整理出的模块：

- `auth`
- `player`
- `publicquery`
- `club`
- `dictionary`
- `platformadmin`
- `opsanalytics`
- `tournament`
- `tournament/appeal`
- `shared`

`appeal` 当前作为 `tournament` 下的子模块处理，而不是独立顶层模块。

## 已完成概述

- 主要 HTTP 路由已迁入各自 `microservices/*/router`。
- 共享 HTTP 支撑层已迁到 `riichinexus/api/http`。
- 顶层 `routes` 薄壳路由已内联到 `ApiRouter` 并删除。
- 主要 application service、query API、tables、responses、requests、OpenAPI schema、JSON codec 已按模块归属整理。
- 共享 API response 已收口到 `microservices/shared/api/responses`。
- OpenAPI schema 已从中心大文件拆到各模块 schema provider。
- API response/view DTO codec 已拆到各模块 `*ResponseCodecs.scala`。
- `ApplicationContext.repositories` 已从生产公开 API 收回，只保留测试专用访问入口。
- `ApplicationAssembly` 已瘦身为 composition root。
- 各模块已有自己的 `*ModuleAssembly`，并下沉到对应模块目录。
- `opsanalytics` 的横切装配已拆分为 diagnostics、repository instrumentation、advanced stats pipeline、projection subscribers、demo scenario 和最终 module context 组装。
- 已完成一轮纯读 repository 编排收口示例：`club member privilege snapshot(s)` 已从 application service 移到 `ClubTables`。
- tournament stage standings / advancement preview / knockout bracket 纯读计算已收口到 `TournamentStageQueryService`，HTTP 读入口和 public detail view 不再依赖 `TournamentApplicationService`。
- 测试大文件已开始按领域拆分：club operations 相关用例已从 `RiichiNexusCompetitionSuite` 拆到 `RiichiNexusClubOperationsSuite`。
- 已删除旧版本残留文件 `api/ApiPlan.scala`、`tables/SchemaVersionTable.scala` 和空旧目录。
- `PostgresOperationalRepositories.scala` 已按 repository ownership 拆分为多个 `Postgres*Repository.scala` 文件。
- `DemoScenarioService.scala` 已拆成对外服务入口和 bootstrap、snapshot、guide/action、共享配置等 support 文件。
- `PostgresRuntime.scala` 已拆分为 `JdbcRuntime.scala`、`PostgresSchemaInitializer.scala` 和 `PostgresAdminService.scala`。
- `AdvancedStatsSupport.scala` 已拆分为对外 facade、`AdvancedStatsMetrics.scala` 和 `AdvancedStatsExactAnalyzer.scala`。
- `TournamentMicroserviceRouter.scala` 已拆分为轻量组合入口，以及 table / query / management / stage 路由文件。

## 当前验证基线

最近一轮验证命令：

```powershell
sbt compile
sbt test
```

最近一轮结果：

- 编译通过。
- 全量测试：92 passed, 0 failed。

## 待实现事项

### 1. 继续拆大文件

当前生产代码中最值得拆的文件：

- `src/main/scala/riichinexus/domain/model/Competition.scala`
- `src/main/scala/riichinexus/domain/service/TournamentRuleEngine.scala`

建议顺序：

1. 处理领域大文件，例如 `Competition.scala` 和 `TournamentRuleEngine.scala`，拆分时要更保守。
2. 如果继续整理 PostgreSQL schema，可把 `PostgresSchemaInitializer.scala` 中的 SQL 按表族移动到声明式 schema definition 文件。

### 2. 继续收口 application service 中的纯读编排

已完成 `ClubTables.memberPrivilegeSnapshot(s)` 和 tournament stage 读模型作为示例。

后续可继续寻找低风险纯读入口：

- 只读、无事务边界。
- 不发布事件。
- 不写 audit。
- 不推进状态机。
- 主要做 repository 查询和 view/projection 拼装。

这类逻辑应逐步移动到模块自己的 `tables` / query API / view assembler 中，让 application service 更聚焦写路径、授权、事务和业务流程。

### 3. 继续整理测试大文件

当前测试 suite 体积偏大：

- `RiichiNexusCoreSuite.scala`
- `ApiServerOperationsSuite.scala`
- `ApiServerCoreSuite.scala`
- `FrontendContractSuite.scala`
- `RiichiNexusCompetitionSuite.scala`

这不是生产结构问题，但后续维护成本会升高。

建议按领域或 API 分组拆分，例如：

- auth / session
- club application / club operations
- tournament scheduling / settlement
- appeal workflow
- dictionary governance
- ops analytics / diagnostics
- frontend contract
