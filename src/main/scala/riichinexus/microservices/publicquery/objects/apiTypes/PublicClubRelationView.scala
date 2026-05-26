package riichinexus.microservices.publicquery.objects.apiTypes

import riichinexus.domain.model.ClubRelation
import upickle.default.*

final case class PublicClubRelationView(
    relation: String
) derives CanEqual

object PublicClubRelationView:
  given ReadWriter[PublicClubRelationView] = macroRW

  def fromDomain(relation: ClubRelation): PublicClubRelationView =
    PublicClubRelationView(relation = relation.relation.toString)
