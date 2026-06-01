package riichinexus.microservices.club.objects.relationmanagement.apiTypes

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.microservices.club.domain.relationmanagement.model.ClubRelation
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import upickle.default.*

final case class UpdateClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
)

object UpdateClubRelationRequest:
  given ReadWriter[UpdateClubRelationRequest] = macroRW
