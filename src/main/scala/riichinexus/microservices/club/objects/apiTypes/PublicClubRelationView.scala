package riichinexus.microservices.club.objects.apiTypes

import riichinexus.microservices.club.domain.model.ClubRelation
import upickle.default.*

final case class PublicClubRelationView(
    relation: String
) derives CanEqual

object PublicClubRelationView:
  given ReadWriter[PublicClubRelationView] = macroRW

  def fromDomain(relation: ClubRelation): PublicClubRelationView =
    PublicClubRelationView(relation = relation.relation.toString)
