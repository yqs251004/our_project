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
)

object UpdateClubRelationRequest:
  given ReadWriter[UpdateClubRelationRequest] = macroRW
