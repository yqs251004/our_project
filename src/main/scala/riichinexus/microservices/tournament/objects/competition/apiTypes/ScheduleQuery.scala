package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ScheduleQuery 表示赛程查询 的列表或详情查询条件。 */

final case class ScheduleQuery(
    tournamentStatus: Option[TournamentStatus] = None,
    stageStatus: Option[StageStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ScheduleQuery:
  given ReadWriter[ScheduleQuery] = macroRW
