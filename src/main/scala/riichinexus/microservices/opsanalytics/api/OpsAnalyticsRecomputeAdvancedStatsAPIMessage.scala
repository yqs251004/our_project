package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.player.api.`private`.ListAllPlayersPrivateAPIMessage

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

import riichinexus.microservices.club.api.`private`.ListClubsPrivateAPIMessage


import riichinexus.microservices.opsanalytics.domain.functions.{AdvancedStatsBoardFunctions, AdvancedStatsRecomputeTaskFunctions}
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBackfillMode, AdvancedStatsRecomputeTask, DashboardOwner}
import riichinexus.microservices.opsanalytics.objects.apiTypes.AdvancedStatsRecomputeRequest
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import upickle.default.ReadWriter

/** 创建高级统计重算任务。 */
final case class OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
    request: AdvancedStatsRecomputeRequest
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      command <- buildCommand(context)
      _ <- RequirePermissionPrivateAPIMessage(command.operator, Permission.ManagePlatformOperations).plan(context)
      tasks <- enqueueRecompute(context, command)
    yield tasks

  private def buildCommand(context: ApiPlanContext): IO[RecomputeAdvancedStatsCommand] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(request.operatorId).plan(context)
      requestedAt <- IO.realTimeInstant
      command <- IO.delay(resolveCommand(operator, requestedAt))
    yield command

  private def resolveCommand(
      operator: AccessPrincipalPrivateView,
      requestedAt: Instant
  ): RecomputeAdvancedStatsCommand =
    validateRequest()
    RecomputeAdvancedStatsCommand(
      operator = operator,
      targetOwner = targetOwner,
      mode = request.mode,
      targetedReason = request.reason.getOrElse("manual-targeted-recompute"),
      fullReason = request.reason.getOrElse("manual-full-recompute"),
      backfillReason = request.reason.getOrElse(
        s"manual-${AdvancedStatsBackfillMode.toString(request.mode).toLowerCase}-backfill"
      ),
      limit = request.limit,
      requestedAt = requestedAt
    )

  private def validateRequest(): Unit =
    if request.ownerType.isDefined != request.ownerId.isDefined then
      throw IllegalArgumentException("ownerType and ownerId must be provided together")
    if request.limit <= 0 then
      throw IllegalArgumentException("Advanced stats recompute limit must be positive")

  private def targetOwner: Option[DashboardOwner] =
    (request.ownerType, request.ownerId) match
      case (Some("player"), Some(id)) => Some(DashboardOwner.Player(PlayerId(id)))
      case (Some("club"), Some(id))   => Some(DashboardOwner.Club(ClubId(id)))
      case (Some(other), Some(_))     => throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
      case _                          => None

  private def enqueueRecompute(
      context: ApiPlanContext,
      command: RecomputeAdvancedStatsCommand
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    command.targetOwner match
      case Some(owner) =>
        enqueueOwners(context, Vector(owner), command.targetedReason, command.requestedAt)
      case None =>
        command.mode match
          case AdvancedStatsBackfillMode.Full =>
            enqueueFullRecompute(
              context,
              requestedAt = command.requestedAt,
              reason = command.fullReason
            )
          case selectedMode =>
            enqueueBackfill(
              context,
              mode = selectedMode,
              requestedAt = command.requestedAt,
              reason = command.backfillReason,
              limit = command.limit
            )

  private def enqueueFullRecompute(
      context: ApiPlanContext,
      requestedAt: Instant,
      reason: String
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      owners <- listAllOwners(context)
      tasks <- enqueueOwners(context, owners, reason, requestedAt)
    yield tasks

  private def enqueueBackfill(
      context: ApiPlanContext,
      mode: AdvancedStatsBackfillMode,
      requestedAt: Instant,
      reason: String,
      limit: Int
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      owners <- listAllOwners(context)
      selectedOwners <- selectBackfillOwners(context, owners, mode, limit)
      tasks <- enqueueOwners(context, selectedOwners, reason, requestedAt)
    yield tasks

  private def listAllOwners(context: ApiPlanContext): IO[Vector[DashboardOwner]] =
    for
      players <- ListAllPlayersPrivateAPIMessage().plan(context)
      clubs <- ListClubsPrivateAPIMessage(activeOnly = true).plan(context)
    yield (players.map(player => DashboardOwner.Player(player.id)) ++ clubs.map(club => DashboardOwner.Club(club.id))).distinct

  private def selectBackfillOwners(
      context: ApiPlanContext,
      owners: Vector[DashboardOwner],
      mode: AdvancedStatsBackfillMode,
      limit: Int
  ): IO[Vector[DashboardOwner]] =
    IO.blocking(owners.filter(owner => shouldBackfillOwner(context.connection, owner, mode)).take(limit))

  private def enqueueOwners(
      context: ApiPlanContext,
      owners: Vector[DashboardOwner],
      reason: String,
      requestedAt: Instant
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    IO.blocking(owners.map(owner => enqueueOwnerRecompute(context.connection, owner, reason, requestedAt)))

  private def enqueueOwnerRecompute(
      connection: java.sql.Connection,
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    {
      AdvancedStatsRecomputeTaskTable
        .findActiveByOwner(connection, owner, AdvancedStatsBoardFunctions.currentCalculatorVersion)
        .getOrElse(
          AdvancedStatsRecomputeTaskTable.save(connection,
            AdvancedStatsRecomputeTaskFunctions.create(
              owner = owner,
              reason = reason,
              requestedAt = requestedAt,
              calculatorVersion = AdvancedStatsBoardFunctions.currentCalculatorVersion,
              lastMatchRecordId = lastMatchRecordId
            )
          )
        )
    }

  private def shouldBackfillOwner(
      connection: java.sql.Connection,
      owner: DashboardOwner,
      mode: AdvancedStatsBackfillMode
  ): Boolean =
    val board = AdvancedStatsBoardTable.findByOwner(connection, owner)
    mode match
      case AdvancedStatsBackfillMode.Full    => true
      case AdvancedStatsBackfillMode.Missing => board.isEmpty
      case AdvancedStatsBackfillMode.Stale =>
        board.exists(_.calculatorVersion < AdvancedStatsBoardFunctions.currentCalculatorVersion)

  private final case class RecomputeAdvancedStatsCommand(
      operator: AccessPrincipalPrivateView,
      targetOwner: Option[DashboardOwner],
      mode: AdvancedStatsBackfillMode,
      targetedReason: String,
      fullReason: String,
      backfillReason: String,
      limit: Int,
      requestedAt: Instant
  )
