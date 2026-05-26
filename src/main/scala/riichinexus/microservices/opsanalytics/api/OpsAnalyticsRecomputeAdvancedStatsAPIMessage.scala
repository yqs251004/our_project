package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.OpsAnalyticsModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.*
import riichinexus.microservices.opsanalytics.objects.apiTypes.AdvancedStatsRecomputeRequest
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
    request: AdvancedStatsRecomputeRequest
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      operator <- IO(context.principal(request.operatorId))
      requestedAt <- IO.realTimeInstant
      module = context.support.opsAnalyticsModule
      command <- IO(resolveCommand(operator, requestedAt))
      _ <- IO(requireOpsAdmin(context, command.operator))
      tasks <- IO(enqueueRecompute(context.connection, module, command))
    yield tasks

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
    context.support.requirePermission(operator, Permission.ManagePlatformOperations)

  private def enqueueRecompute(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      command: RecomputeAdvancedStatsCommand
  ): Vector[AdvancedStatsRecomputeTask] =
    command.targetOwner match
      case Some(owner) =>
        Vector(
          enqueueOwnerRecompute(
            connection,
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
              connection,
              module,
              requestedAt = command.requestedAt,
              reason = command.fullReason
            )
          case selectedMode =>
            enqueueBackfill(
              connection,
              module,
              mode = selectedMode,
              requestedAt = command.requestedAt,
              reason = command.backfillReason,
              limit = command.limit
            )

  private def enqueueFullRecompute(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      requestedAt: Instant,
      reason: String
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      PlayerTable.findAll(connection).map(player => DashboardOwner.Player(player.id)) ++
        riichinexus.microservices.club.tables.club.ClubTable.findActive(connection).map(club => DashboardOwner.Club(club.id))

    owners.distinct.map(owner => enqueueOwnerRecompute(connection, module, owner, reason, requestedAt))

  private def enqueueBackfill(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      mode: AdvancedStatsBackfillMode,
      requestedAt: Instant,
      reason: String,
      limit: Int
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      PlayerTable.findAll(connection).map(player => DashboardOwner.Player(player.id)) ++
        riichinexus.microservices.club.tables.club.ClubTable.findActive(connection).map(club => DashboardOwner.Club(club.id))

    owners.distinct
      .filter(owner => shouldBackfillOwner(connection, module, owner, mode))
      .take(limit)
      .map(owner => enqueueOwnerRecompute(connection, module, owner, reason, requestedAt))

  private def enqueueOwnerRecompute(
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    module.transactionManager.inTransaction {
      riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
        .findActiveByOwner(connection, owner, AdvancedStatsBoard.CurrentCalculatorVersion)
        .getOrElse(
          riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(connection, 
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
      connection: java.sql.Connection,
      module: OpsAnalyticsModuleContext,
      owner: DashboardOwner,
      mode: AdvancedStatsBackfillMode
  ): Boolean =
    val board = riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, owner)
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
