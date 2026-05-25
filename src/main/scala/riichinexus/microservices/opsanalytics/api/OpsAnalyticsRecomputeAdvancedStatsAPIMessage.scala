package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{AdvancedStatsRecomputeRequest, AdvancedStatsRecomputeTask as AdvancedStatsRecomputeTaskResponse}
import upickle.default.*

final case class OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
    request: AdvancedStatsRecomputeRequest
) extends APIMessage[Vector[AdvancedStatsRecomputeTaskResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTaskResponse]] =
    for
      operator <- IO(context.support.principal(request.operatorId))
      requestedAt <- IO.realTimeInstant
      module = context.support.opsAnalyticsModule
      command <- IO(resolveCommand(operator, requestedAt))
      _ <- IO(requireOpsAdmin(context, command.operator))
      tasks <- IO(enqueueRecompute(module, command))
    yield tasks.map(AdvancedStatsRecomputeTaskResponse.fromDomain)

  private def resolveCommand(
      operator: AccessPrincipal,
      requestedAt: Instant
  ): RecomputeAdvancedStatsCommand =
    RecomputeAdvancedStatsCommand(
      operator = operator,
      targetOwner = request.targetOwner,
      mode = request.mode,
      targetedReason = request.targetedReason,
      fullReason = request.fullReason,
      backfillReason = request.backfillReason,
      limit = request.limit,
      requestedAt = requestedAt
    )

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)

  private def enqueueRecompute(
      module: OpsAnalyticsModuleContext,
      command: RecomputeAdvancedStatsCommand
  ): Vector[AdvancedStatsRecomputeTask] =
    command.targetOwner match
      case Some(owner) =>
        Vector(
          enqueueOwnerRecompute(
            module,
            owner = owner,
            reason = command.targetedReason,
            requestedAt = command.requestedAt
          )
        )
      case None =>
        command.mode match
          case AdvancedStatsBackfillMode.Full =>
            enqueueFullRecompute(
              module,
              requestedAt = command.requestedAt,
              reason = command.fullReason
            )
          case selectedMode =>
            enqueueBackfill(
              module,
              mode = selectedMode,
              requestedAt = command.requestedAt,
              reason = command.backfillReason,
              limit = command.limit
            )

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

  private final case class RecomputeAdvancedStatsCommand(
      operator: AccessPrincipal,
      targetOwner: Option[DashboardOwner],
      mode: AdvancedStatsBackfillMode,
      targetedReason: String,
      fullReason: String,
      backfillReason: String,
      limit: Int,
      requestedAt: Instant
  )
