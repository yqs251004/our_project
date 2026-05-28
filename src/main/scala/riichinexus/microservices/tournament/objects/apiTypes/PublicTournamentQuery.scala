package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.microservices.tournament.objects.TournamentStatus
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PublicTournamentQuery(
    status: Option[TournamentStatus] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

object PublicTournamentQuery:
  given ReadWriter[PublicTournamentQuery] = macroRW
