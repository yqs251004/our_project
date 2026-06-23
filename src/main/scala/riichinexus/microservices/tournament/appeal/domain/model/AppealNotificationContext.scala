package riichinexus.microservices.tournament.appeal.domain.model

import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.stage.model.Table

/** 生成申诉通知文案时需要的赛事、牌桌和阶段上下文。
  *
  * 通知组装逻辑使用该模型避免重复查询，并保持通知字段来源集中。
  */
private[appeal] final case class AppealNotificationContext(
    tournament: Tournament,
    table: Table,
    stageName: String
)
