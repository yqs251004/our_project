package riichinexus.microservices.club.objects.apiTypes

import upickle.default.*

final case class ClubTournamentQuery(
    scope: Option[String] = None,
    viewer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubTournamentQuery:
  given ReadWriter[ClubTournamentQuery] = macroRW
