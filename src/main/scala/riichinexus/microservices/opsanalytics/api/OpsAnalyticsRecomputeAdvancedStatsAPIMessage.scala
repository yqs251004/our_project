package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import cats.effect.unsafe.implicits.global
import riichinexus.system.api.ApiPlanContext
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.api.`private`.{ListClubsPrivateAPIMessage, ResolveClubPrivateAPIMessage, ResolveClubsPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.{
  AdvancedStatsBoardFunctions,
  AdvancedStatsRecomputeTaskFunctions
}
import riichinexus.microservices.opsanalytics.objects.*
import riichinexus.microservices.opsanalytics.objects.apiTypes.AdvancedStatsRecomputeRequest
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
    request: AdvancedStatsRecomputeRequest
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      operator <- IO.blocking(ResolveAccessPrincipal(request.operatorId).resolve(context.connection))
      requestedAt <- IO.realTimeInstant
      command <- IO.blocking(resolveCommand(operator, requestedAt))
      _ <- requireOpsAdmin(context, command.operator)
      tasks <- IO.blocking(enqueueRecompute(context.connection, command))
    yield tasks

  private def resolveCommand(
      operator: AccessPrincipal,
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

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      principal = Some(operator),
      permission = Permission.ManagePlatformOperations
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to manage platform operations"))
    }

  private def enqueueRecompute(
      connection: java.sql.Connection,
      command: RecomputeAdvancedStatsCommand
  ): Vector[AdvancedStatsRecomputeTask] =
    command.targetOwner match
      case Some(owner) =>
        Vector(
          enqueueOwnerRecompute(
            connection,
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
              requestedAt = command.requestedAt,
              reason = command.fullReason
            )
          case selectedMode =>
            enqueueBackfill(
              connection,
              mode = selectedMode,
              requestedAt = command.requestedAt,
              reason = command.backfillReason,
              limit = command.limit
            )

  private def enqueueFullRecompute(
      connection: java.sql.Connection,
      requestedAt: Instant,
      reason: String
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      PlayerPersistenceFunctions.findAllPlayers(connection).map(player => DashboardOwner.Player(player.id)) ++
        ListClubsPrivateAPIMessage(activeOnly = true).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().map(club => DashboardOwner.Club(club.id))

    owners.distinct.map(owner => enqueueOwnerRecompute(connection, owner, reason, requestedAt))

  private def enqueueBackfill(
      connection: java.sql.Connection,
      mode: AdvancedStatsBackfillMode,
      requestedAt: Instant,
      reason: String,
      limit: Int
  ): Vector[AdvancedStatsRecomputeTask] =
    val owners =
      PlayerPersistenceFunctions.findAllPlayers(connection).map(player => DashboardOwner.Player(player.id)) ++
        ListClubsPrivateAPIMessage(activeOnly = true).plan(ApiPlanContext(support = null, bearerToken = None, connection = connection)).unsafeRunSync().map(club => DashboardOwner.Club(club.id))

    owners.distinct
      .filter(owner => shouldBackfillOwner(connection, owner, mode))
      .take(limit)
      .map(owner => enqueueOwnerRecompute(connection, owner, reason, requestedAt))

  private def enqueueOwnerRecompute(
      connection: java.sql.Connection,
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    {
      riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
        .findActiveByOwner(connection, owner, AdvancedStatsBoardFunctions.currentCalculatorVersion)
        .getOrElse(
          riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(connection, 
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
    val board = riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, owner)
    mode match
      case AdvancedStatsBackfillMode.Full    => true
      case AdvancedStatsBackfillMode.Missing => board.isEmpty
      case AdvancedStatsBackfillMode.Stale =>
        board.exists(_.calculatorVersion < AdvancedStatsBoardFunctions.currentCalculatorVersion)

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
