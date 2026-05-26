package riichinexus.microservices.club.objects.apiTypes

import riichinexus.microservices.club.domain.model.ClubRankNode
import upickle.default.*

final case class ClubRankNodeRequest(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[String] = Vector.empty
):
  def toNode: ClubRankNode =
    ClubRankNode(
      code = code,
      label = label,
      minimumContribution = minimumContribution,
      privileges = privileges
    )

object ClubRankNodeRequest:
  given ReadWriter[ClubRankNodeRequest] = macroRW
