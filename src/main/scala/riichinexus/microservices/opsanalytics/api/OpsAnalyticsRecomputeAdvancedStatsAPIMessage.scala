package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
    operatorId: PlayerId,
    mode: AdvancedStatsBackfillMode = AdvancedStatsBackfillMode.Full,
    ownerType: Option[String] = None,
    ownerId: Option[String] = None,
    reason: Option[String] = None,
    limit: Int = 500
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  require(ownerType.nonEmpty == ownerId.nonEmpty, "ownerType and ownerId must be provided together")
  require(limit > 0, "Advanced stats recompute limit must be positive")

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
      val module = context.support.opsAnalyticsModule
      val requestedAt = Instant.now()
      (ownerType, ownerId) match
        case (Some("player"), Some(id)) =>
          Vector(
            enqueueOwnerRecompute(
              module,
              owner = DashboardOwner.Player(PlayerId(id)),
              reason = reason.getOrElse("manual-targeted-recompute"),
              requestedAt = requestedAt
            )
          )
        case (Some("club"), Some(id)) =>
          Vector(
            enqueueOwnerRecompute(
              module,
              owner = DashboardOwner.Club(ClubId(id)),
              reason = reason.getOrElse("manual-targeted-recompute"),
              requestedAt = requestedAt
            )
          )
        case (Some(other), Some(_)) =>
          throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
        case _ =>
          mode match
            case AdvancedStatsBackfillMode.Full =>
              enqueueFullRecompute(
                module,
                requestedAt = requestedAt,
                reason = reason.getOrElse("manual-full-recompute")
              )
            case selectedMode =>
              enqueueBackfill(
                module,
                mode = selectedMode,
                requestedAt = requestedAt,
                reason = reason.getOrElse(s"manual-${selectedMode.toString.toLowerCase}-backfill"),
                limit = limit
              )
    }

  private def enqueueFullRecompute(
      module: OpsAnalyticsModuleContext,
      requestedAt: Instant,
      reason: String
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      module.playerRepository.findAll().map(player => DashboardOwner.Player(player.id)) ++
        module.clubRepository.findActive().map(club => DashboardOwner.Club(club.id))

    owners.distinct.map(owner => enqueueOwnerRecompute(module, owner, reason, requestedAt))

  private def enqueueBackfill(
      module: OpsAnalyticsModuleContext,
      mode: AdvancedStatsBackfillMode,
      requestedAt: Instant,
      reason: String,
      limit: Int
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      module.playerRepository.findAll().map(player => DashboardOwner.Player(player.id)) ++
        module.clubRepository.findActive().map(club => DashboardOwner.Club(club.id))

    owners.distinct
      .filter(owner => shouldBackfillOwner(module, owner, mode))
      .take(limit)
      .map(owner => enqueueOwnerRecompute(module, owner, reason, requestedAt))

  private def enqueueOwnerRecompute(
      module: OpsAnalyticsModuleContext,
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    module.transactionManager.inTransaction {
      module.advancedStatsRecomputeTaskRepository
        .findActiveByOwner(owner, AdvancedStatsBoard.CurrentCalculatorVersion)
        .getOrElse(
          module.advancedStatsRecomputeTaskRepository.save(
            AdvancedStatsRecomputeTask.create(
              owner = owner,
              reason = reason,
              requestedAt = requestedAt,
              calculatorVersion = AdvancedStatsBoard.CurrentCalculatorVersion,
              lastMatchRecordId = lastMatchRecordId
            )
          )
        )
    }

  private def shouldBackfillOwner(
      module: OpsAnalyticsModuleContext,
      owner: DashboardOwner,
      mode: AdvancedStatsBackfillMode
  ): Boolean =
    val board = module.advancedStatsBoardRepository.findByOwner(owner)
    mode match
      case AdvancedStatsBackfillMode.Full    => true
      case AdvancedStatsBackfillMode.Missing => board.isEmpty
      case AdvancedStatsBackfillMode.Stale =>
        board.exists(_.calculatorVersion < AdvancedStatsBoard.CurrentCalculatorVersion)
