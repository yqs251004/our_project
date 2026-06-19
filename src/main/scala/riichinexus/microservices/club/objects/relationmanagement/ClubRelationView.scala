package riichinexus.microservices.club.objects.relationmanagement

import riichinexus.microservices.club.domain.relationmanagement.model.{ClubRelation as DomainClubRelation}
import upickle.default.ReadWriter

/** ClubRelationView 表示俱乐部关系视图 的前端展示视图，包含targetClubId、relation。 */

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
