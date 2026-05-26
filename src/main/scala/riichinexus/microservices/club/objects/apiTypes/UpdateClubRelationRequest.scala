package riichinexus.microservices.club.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{ClubId, ClubRelation, ClubRelationKind, PlayerId}
import upickle.default.*

final case class UpdateClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def toRelation(updatedAt: Instant = Instant.now()): ClubRelation =
    ClubRelation(
      targetClubId = ClubId(targetClubId),
      relation = ClubRelationKind.valueOf(relation),
      updatedAt = updatedAt,
      note = note
    )

object UpdateClubRelationRequest:
  given ReadWriter[UpdateClubRelationRequest] = macroRW
