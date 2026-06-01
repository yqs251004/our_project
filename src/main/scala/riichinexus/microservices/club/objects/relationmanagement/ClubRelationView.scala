package riichinexus.microservices.club.objects.relationmanagement

import riichinexus.microservices.club.domain.relationmanagement.model.{ClubRelation as DomainClubRelation}
import upickle.default.*

final case class ClubRelationView(
    targetClubId: String,
    relation: ClubRelationKind
) derives ReadWriter

object ClubRelationView:
  def fromDomain(relation: DomainClubRelation): ClubRelationView =
    ClubRelationView(
      targetClubId = relation.targetClubId.value,
      relation = relation.relation
    )
