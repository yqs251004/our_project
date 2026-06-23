package riichinexus.microservices.tournament.domain.stage.model

import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord

/** 计算阶段晋级、排名或淘汰赛结构时需要的聚合上下文。
  *
  * 阶段查询逻辑使用该模型将赛事、阶段、参赛者和对局记录一次性传递给规则引擎。
  */
private[tournament] final case class StageComputationContext(
    tournament: Tournament,
    stage: TournamentStage,
    participants: Vector[PlayerPrivateView],
    records: Vector[MatchRecord]
)
