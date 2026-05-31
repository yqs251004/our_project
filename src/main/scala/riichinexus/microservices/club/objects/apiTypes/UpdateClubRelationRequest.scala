package riichinexus.microservices.club.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.microservices.club.domain.model.ClubRelation
import riichinexus.microservices.club.objects.ClubRelationKind
import upickle.default.*

final case class UpdateClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def toRelation(updatedAt: Instant = Instant.now()): ClubRelation =
    ClubRelation(
      targetClubId = ClubId(targetClubId),
      relation = relation,
      updatedAt = updatedAt,
      note = note
    )

object UpdateClubRelationRequest:
  given ReadWriter[UpdateClubRelationRequest] = macroRW
