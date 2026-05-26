package riichinexus.microservices.publicquery.objects.apiTypes

import riichinexus.microservices.club.domain.model.ClubRelationKind
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PublicClubQuery(
    name: Option[String] = None,
    relation: Option[ClubRelationKind] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

object PublicClubQuery:
  given ReadWriter[PublicClubQuery] = macroRW
