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
):
  require(ownerType.nonEmpty == ownerId.nonEmpty, "ownerType and ownerId must be provided together")
  require(limit > 0, "Advanced stats recompute limit must be positive")

  def targetOwner: Option[DashboardOwner] =
    (ownerType, ownerId) match
      case (Some("player"), Some(id)) => Some(DashboardOwner.Player(PlayerId(id)))
      case (Some("club"), Some(id))   => Some(DashboardOwner.Club(ClubId(id)))
      case (Some(other), Some(_))     => throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
      case _                          => None

  def targetedReason: String =
    reason.getOrElse("manual-targeted-recompute")

  def fullReason: String =
    reason.getOrElse("manual-full-recompute")

  def backfillReason: String =
    reason.getOrElse(s"manual-${mode.toString.toLowerCase}-backfill")

object AdvancedStatsRecomputeRequest:
  given ReadWriter[AdvancedStatsRecomputeRequest] = macroRW
