package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.{ClubRankNode, PlayerId}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateClubRankTreeRequest(
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def nodes: Vector[ClubRankNode] =
    ranks.map(_.toNode)

object UpdateClubRankTreeRequest:
  given ReadWriter[UpdateClubRankTreeRequest] = macroRW
