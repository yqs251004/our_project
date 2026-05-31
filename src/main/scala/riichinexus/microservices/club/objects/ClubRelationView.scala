package riichinexus.microservices.club.objects

import riichinexus.microservices.club.domain.model.{ClubRelation as DomainClubRelation}
import upickle.default.*

final case class ClubRelationView(
    relation: ClubRelationKind
) derives ReadWriter

object ClubRelationView:
  def fromDomain(relation: DomainClubRelation): ClubRelationView =
    ClubRelationView(relation = relation.relation)
