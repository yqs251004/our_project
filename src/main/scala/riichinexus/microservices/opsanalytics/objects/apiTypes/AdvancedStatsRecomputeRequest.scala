package riichinexus.microservices.opsanalytics.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{DashboardOwner, AdvancedStatsBackfillMode}
import upickle.default.*

final case class AdvancedStatsRecomputeRequest(
    operatorId: PlayerId,
    mode: AdvancedStatsBackfillMode = AdvancedStatsBackfillMode.Full,
    ownerType: Option[String] = None,
    ownerId: Option[String] = None,
    reason: Option[String] = None,
    limit: Int = 500
)

object AdvancedStatsRecomputeRequest:
  given ReadWriter[AdvancedStatsRecomputeRequest] = macroRW
