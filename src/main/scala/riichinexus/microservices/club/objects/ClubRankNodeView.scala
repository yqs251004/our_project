package riichinexus.microservices.club.objects

import riichinexus.microservices.club.domain.model.{ClubRankNode as DomainClubRankNode}
import upickle.default.*

final case class ClubRankNodeView(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[ClubPrivilegeCode]
) derives ReadWriter

object ClubRankNodeView:
  def fromDomain(node: DomainClubRankNode): ClubRankNodeView =
    ClubRankNodeView(
      code = node.code,
      label = node.label,
      minimumContribution = node.minimumContribution,
      privileges = node.privileges
    )
