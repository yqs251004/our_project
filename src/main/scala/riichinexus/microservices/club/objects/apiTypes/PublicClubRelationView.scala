package riichinexus.microservices.club.objects.apiTypes

import riichinexus.microservices.club.domain.model.ClubRelation
import riichinexus.microservices.club.objects.ClubRelationKind
import upickle.default.*

final case class PublicClubRelationView(
    relation: ClubRelationKind
) derives CanEqual

object PublicClubRelationView:
  given ReadWriter[PublicClubRelationView] = macroRW

  def fromDomain(relation: ClubRelation): PublicClubRelationView =
    PublicClubRelationView(relation = relation.relation)
