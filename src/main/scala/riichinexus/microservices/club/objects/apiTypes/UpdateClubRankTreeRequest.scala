package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.domain.model.ClubRankNode
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
