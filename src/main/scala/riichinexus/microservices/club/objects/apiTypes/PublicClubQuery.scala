package riichinexus.microservices.club.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.ClubRelationKind
import upickle.default.*

final case class PublicClubQuery(
    name: Option[String] = None,
    relation: Option[ClubRelationKind] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

object PublicClubQuery:
  given ReadWriter[PublicClubQuery] = macroRW
