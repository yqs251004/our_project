package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import upickle.default.*

final case class AdjustClubMemberContributionRequest(
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
)

object AdjustClubMemberContributionRequest:
  given ReadWriter[AdjustClubMemberContributionRequest] = macroRW
