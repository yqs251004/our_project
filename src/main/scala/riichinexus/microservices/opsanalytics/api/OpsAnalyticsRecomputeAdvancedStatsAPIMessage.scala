package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ListAllPlayersPrivateAPIMessage

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView

import riichinexus.microservices.club.api.profile.`private`.ListClubsPrivateAPIMessage


import riichinexus.microservices.opsanalytics.domain.functions.{AdvancedStatsBoardFunctions, AdvancedStatsRecomputeTaskFunctions}
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBackfillMode, AdvancedStatsRecomputeTask, DashboardOwner}
import riichinexus.microservices.opsanalytics.objects.apiTypes.AdvancedStatsRecomputeRequest
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
/** 创建高级统计重算任务。 */
final case class OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
    request: AdvancedStatsRecomputeRequest
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]]:

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(request.operatorId).plan(context)
      requestedAt <- IO.realTimeInstant
      _ <- IO.delay(validateRequest())
      targetOwner = resolveTargetOwner
      targetedReason = request.reason.getOrElse("manual-targeted-recompute")
      fullReason = request.reason.getOrElse("manual-full-recompute")
      backfillReason = request.reason.getOrElse(
        s"manual-${AdvancedStatsBackfillMode.toString(request.mode).toLowerCase}-backfill"
      )
      _ <- RequirePermissionPrivateAPIMessage(operator, Permission.ManagePlatformOperations).plan(context)
      tasks <- enqueueRecompute(context, targetOwner, request.mode, targetedReason, fullReason, backfillReason, request.limit, requestedAt)
    yield tasks

  private def validateRequest(): Unit =
    if request.ownerType.isDefined != request.ownerId.isDefined then
      throw IllegalArgumentException("ownerType and ownerId must be provided together")
    if request.limit <= 0 then
      throw IllegalArgumentException("Advanced stats recompute limit must be positive")

  private def resolveTargetOwner: Option[DashboardOwner] =
    (request.ownerType, request.ownerId) match
      case (Some("player"), Some(id)) => Some(DashboardOwner.Player(PlayerId(id)))
      case (Some("club"), Some(id))   => Some(DashboardOwner.Club(ClubId(id)))
      case (Some(other), Some(_))     => throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
      case _                          => None

  private def enqueueRecompute(
      context: ApiPlanContext,
      targetOwner: Option[DashboardOwner],
      mode: AdvancedStatsBackfillMode,
      targetedReason: String,
      fullReason: String,
      backfillReason: String,
      limit: Int,
      requestedAt: Instant
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    targetOwner match
      case Some(owner) =>
        enqueueOwners(context, Vector(owner), targetedReason, requestedAt)
      case None =>
        mode match
          case AdvancedStatsBackfillMode.Full =>
            enqueueFullRecompute(
              context,
              requestedAt = requestedAt,
              reason = fullReason
            )
          case selectedMode =>
            enqueueBackfill(
              context,
              mode = selectedMode,
              requestedAt = requestedAt,
              reason = backfillReason,
              limit = limit
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
