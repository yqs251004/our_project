package riichinexus.microservices.club.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.domain.model.ClubHonor
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AwardClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
)

object AwardClubHonorRequest:
  given ReadWriter[AwardClubHonorRequest] = macroRW
