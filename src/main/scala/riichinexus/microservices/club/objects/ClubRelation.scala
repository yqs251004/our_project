package riichinexus.microservices.club.objects

import riichinexus.domain.model.{ClubRelation as DomainClubRelation}
import upickle.default.*

final case class ClubRelation(
    relation: String
) derives ReadWriter

object ClubRelation:
  def fromDomain(relation: DomainClubRelation): ClubRelation =
    ClubRelation(relation = relation.relation.toString)
