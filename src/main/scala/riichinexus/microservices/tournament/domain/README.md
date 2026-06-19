# tournament domain 目录语义

`domain` 放赛事微服务的后端领域模型和领域函数。`objects` 是前端镜像和 API 边界类型，`tables` 是表读写，`api` 负责权限、事务和跨微服务编排。

## management 目录边界

- `tournamentmanagement` 是赛事聚合根语义，负责赛事生命周期、阶段列表、报名名单、白名单、管理员和赛事状态。
- `rulesmanagement` 是阶段规则语义，负责赛制、晋级、排名、瑞士轮、淘汰赛等规则计算和规则投影；它读取阶段数据，但不拥有赛事聚合或比赛桌生命周期。
- `lineupmanagement` 是阵容提交语义，负责俱乐部/选手在某阶段的出场、替补和提交记录；它被排桌和规则计算消费。
- `tablemanagement` 是比赛桌语义，负责桌、座位、排桌计划、开局/重置/归档前的桌状态；它可以消费阵容、规则和历史记录来生成桌计划。
- `recordmanagement` 是已完成对局记录语义，负责归档后的标准比赛结果、座位结果和对局记录查询。
- `paifumanagement` 是牌谱语义，负责上传牌谱、牌谱动作、局结果、和种、分数变化等麻将牌谱事实；它不负责赛事桌生命周期。
- `settlementmanagement` 是结算语义，负责奖池、调整、结算快照和最终分配；它消费排名、记录和赛事配置，不反向修改规则/排桌。
- 根目录 `functions` 只放跨本微服务通用且无法归入具体 management 的后端函数，例如 ID 生成器。

## 依赖方向

领域函数可以在 tournament 微服务内部按业务需要组合其它 tournament domain 语义，但不能直接调用其它微服务的 `domain.functions`。跨微服务读取或写入必须通过对应 public/private API。

如果一个函数需要 SQL，它应当调用本微服务 `tables` 中已有的单表函数；如果需要跨微服务协作，它应当位于 API plan 或明确的 domain coordinator 中，并通过 APIMessage 串联。
