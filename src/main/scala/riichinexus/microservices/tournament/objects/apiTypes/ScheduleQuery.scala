package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.microservices.tournament.objects.{StageStatus, TournamentStatus}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ScheduleQuery(
    tournamentStatus: Option[TournamentStatus] = None,
    stageStatus: Option[StageStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

object ScheduleQuery:
  given ReadWriter[ScheduleQuery] = macroRW
