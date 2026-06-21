package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 查询公共赛程列表的状态过滤和分页参数。
  *
  * 它可以同时限定赛事状态和阶段状态，让大厅只展示当前关注的报名、进行中或已完成赛程。
  */
final case class ScheduleQuery(
    tournamentStatus: Option[TournamentStatus] = None,
    stageStatus: Option[StageStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ScheduleQuery:
  given ReadWriter[ScheduleQuery] = macroRW
