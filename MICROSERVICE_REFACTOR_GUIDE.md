# 后端结构化微服务重构指南

## 目标

本项目当前不做“独立部署、独立数据库、独立 `build.sbt`”式的强微服务拆分。

本次重构目标是：

- 保持一个后端工程、一个启动入口、一个进程
- 在 `src/main/scala` 下按微服务边界重构目录
- 每个微服务只保留四类目录：
  - `api/`
  - `objects/`
  - `tables/`
  - `router/`
- 微服务之间通过各自 `api/` 下定义的进程内 API 文件交流
- 先完成结构抽离，再考虑更强的解耦

## 总目录

```text
src/main/scala/riichinexus/
  bootstrap/
  domain/
  infrastructure/
  microservices/
    opsanalytics/
      api/
      objects/
      tables/
      router/
    auth/
      api/
      objects/
      tables/
      router/
    publicquery/
      api/
      objects/
      tables/
      router/
    club/
      api/
      objects/
      tables/
      router/
    competition/
      api/
      objects/
      tables/
      router/
    appeal/
      api/
      objects/
      tables/
      router/
    dictionary/
      api/
      objects/
      tables/
      router/
    platformadmin/
      api/
      objects/
      tables/
      router/
```

## 每个微服务的最轻结构

每个微服务只保留下面四类目录：

```text
microservices/opsanalytics/
  api/
  objects/
  tables/
  router/
```

### `api/`

`api/` 不是一个单文件入口，也不是只放 DTO。

这里按你的要求保留：

- `api/requests/`
- `api/responses/`

剩下原本可能会塞进一个 `OpsAnalyticsApi.scala` 的内容，改成按功能拆成多个 `*.api.scala` 文件，直接放在 `api/` 下。

推荐形式：

```text
microservices/opsanalytics/api/
  requests/
    advanced-stats.requests.scala
    domain-events.requests.scala
  responses/
    advanced-stats.responses.scala
    domain-events.responses.scala
    performance.responses.scala
  advanced-stats.api.scala
  domain-events.api.scala
  performance.api.scala
```

说明：

- `requests/`
  放请求体、查询参数对象、批处理入参，按功能拆文件
- `responses/`
  放响应对象，按功能拆文件
- `*.api.scala`
  放真正的进程内调用入口和编排逻辑，但按功能拆多个文件

例如：

- `advanced-stats.api.scala`
  放高级统计相关入口
- `domain-events.api.scala`
  放 domain event 运维入口
- `performance.api.scala`
  放 diagnostics / metrics 入口

这里不再要求有一个总的 `OpsAnalyticsApi.scala`。

补充说明：

- 你提到的 `/contrasts` 应该是 `/contracts` 的笔误
- 本指南现在已经去掉 `contracts` 这一层，不再单独保留

### `objects/`

`objects/` 放这个微服务私有的辅助对象，不放跨项目共享的大领域模型。

适合放：

- 分页对象
- filter 对象
- 错误对象
- 本服务私有的 summary / view object
- 本服务内部拼装用的 lightweight model

不适合放：

- 全局 `Player`、`Club`、`Tournament` 这类已有共享领域模型
- 基础设施类

### `tables/`

`tables/` 放本微服务负责维护的表操作或表说明。

如果暂时还没有真正物理拆库，也要先按 ownership 摆好：

- `opsanalytics/tables/advanced-stats-recompute-task.table.scala`
- `opsanalytics/tables/event-cascade-record.table.scala`
- `opsanalytics/tables/domain-event-observability.table.scala`

### `router/`

`router/` 只放 HTTP 路由。

路由负责：

- 解析请求
- 调用本微服务 `api/` 下的能力
- 返回响应

路由不负责：

- 直接跨微服务访问别人的表
- 写复杂业务编排
- 直接拼装大量内部 helper

## 跨微服务调用规则

本次不是网络级微服务，所以“通过 API 交流”指的是：

- 只能依赖别的微服务 `api/requests`、`api/responses` 和 `api/*.api.scala`
- 只能调用别的微服务 `api/*.api.scala` 暴露的入口
- 不能直接 import 别的微服务的 `tables/`
- 不能直接 import 别的微服务的 `objects/` 里私有辅助对象
- 不能直接从别的微服务 router 倒用逻辑

一句话：

- **跨服务只看 `api/`**
- **服务私有实现藏在自己的目录里**

## 微服务映射建议

### `opsanalytics`

负责：

- advanced stats 管理接口
- performance diagnostics
- domain event outbox 运维观察
- event cascade records 管理视图

### `auth`

负责：

- register
- login
- logout
- session restore
- guest session

### `publicquery`

负责：

- public schedules
- public tournaments
- public clubs
- public leaderboards
- public dashboards

### `club`

负责：

- clubs core
- members
- applications
- club 与 tournament 的俱乐部侧动作

### `competition`

负责：

- tournaments
- tables
- records
- stage / lineup / schedule / standings

### `appeal`

负责：

- appeals
- workflow
- adjudication

### `dictionary`

负责：

- dictionary
- namespaces
- runtime config

### `platformadmin`

负责：

- ban player
- dissolve club
- grant super admin
- 非 opsanalytics 的平台治理动作

## 迁移顺序

每次只迁一个微服务，顺序固定：

1. 建立 `microservices/<service>/api`
2. 在 `api/requests`、`api/responses` 下先放 DTO
3. 把原本会塞进单个 `*Api.scala` 的内容拆成多个 `*.api.scala`
4. 建立 `objects`
5. 建立 `tables`
6. 建立 `router`
7. 从旧 `routes/*` 挪路由
8. 从旧 `application/service/*` 挪对应编排逻辑到多个 `*.api.scala`
9. 把旧调用改成依赖新 `api/`
10. 双跑验证后删除旧位置代码

## `opsanalytics` 先行试点

第一批建议先落：

```text
microservices/opsanalytics/
  api/
    requests/
      advanced-stats.requests.scala
      domain-events.requests.scala
    responses/
      advanced-stats.responses.scala
      domain-events.responses.scala
      performance.responses.scala
    advanced-stats.api.scala
    domain-events.api.scala
    performance.api.scala
  objects/
    filters.scala
    pagination.scala
  tables/
    advanced-stats-recompute-task.table.scala
    event-cascade-record.table.scala
  router/
    OpsAnalyticsAdminRouter.scala
```

## 删除旧代码的条件

只有下面条件都满足，才允许删除旧实现：

- 新目录已承接全部对应 HTTP 路由
- 新 `api/` 已成为唯一调用入口
- 旧路由不再被 `ApiRouter` 挂载
- 旧 `Services.scala` 中对应片段已经没有调用者
- 测试与前端联调通过

## 本指南的边界

本指南明确不做下面这些事情：

- 不拆独立 `build.sbt`
- 不拆独立部署
- 不拆独立数据库
- 不做服务间 HTTP 真互调
- 不做消息总线级解耦

本指南只处理：

- `src/main/scala` 内的目录边界重构
- 服务间调用入口收敛
- 旧大文件拆散
