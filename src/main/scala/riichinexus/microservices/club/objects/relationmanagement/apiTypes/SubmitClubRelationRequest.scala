package riichinexus.microservices.club.objects.relationmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import upickle.default.*

final case class SubmitClubRelationRequest(
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
)

object SubmitClubRelationRequest:
  given ReadWriter[SubmitClubRelationRequest] = macroRW
