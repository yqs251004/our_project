package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.*

final case class PublicClubLeaderboardQuery(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

object PublicClubLeaderboardQuery:
  given ReadWriter[PublicClubLeaderboardQuery] = macroRW
