package riichinexus.microservices.club.objects.tournamentparticipation.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ClubTournamentQuery(
    scope: Option[String] = None,
    viewer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubTournamentQuery:
  given ReadWriter[ClubTournamentQuery] = macroRW
