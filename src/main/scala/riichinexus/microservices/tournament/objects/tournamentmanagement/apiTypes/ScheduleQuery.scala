package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentStatus}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ScheduleQuery(
    tournamentStatus: Option[TournamentStatus] = None,
    stageStatus: Option[StageStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ScheduleQuery:
  given ReadWriter[ScheduleQuery] = macroRW
