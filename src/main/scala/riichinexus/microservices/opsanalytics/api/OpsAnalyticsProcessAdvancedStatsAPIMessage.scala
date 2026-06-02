package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import cats.effect.unsafe.implicits.global
import riichinexus.system.api.ApiPlanContext
import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.errors.OptimisticConcurrencyException
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
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.api.`private`.{ListClubsPrivateAPIMessage, ResolveClubPrivateAPIMessage, ResolveClubsPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsBoardFunctions
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsRecomputeTaskFunctions
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.api.`private`.{
  ListPlayerMatchRecordsPrivateAPIMessage,
  ListPlayerPaifusPrivateAPIMessage
}
import upickle.default.*

final case class OpsAnalyticsProcessAdvancedStatsAPIMessage(
    operatorId: PlayerId,
    limit: Int = 50
) extends APIMessage[Vector[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[AdvancedStatsRecomputeTask]] =
    for
      _ <- IO.blocking(validateLimit())
      operator <- IO.blocking(ResolveAccessPrincipal(operatorId).resolve(context.connection))
      processedAt <- IO.realTimeInstant
      command = ProcessAdvancedStatsCommand(operator, limit, processedAt)
      _ <- requireOpsAdmin(context, command.operator)
      tasks <- IO.blocking(processPending(context.connection, command))
    yield tasks

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      principal = Some(operator),
      permission = Permission.ManagePlatformOperations
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to manage platform operations"))
    }

  private def validateLimit(): Unit =
    if limit <= 0 then throw IllegalArgumentException("Advanced stats task processing limit must be positive")

  private val retryDelay = Duration.ofMinutes(5)
  private val maxAttempts = 3

  private def processPending(
      connection: java.sql.Connection,
      command: ProcessAdvancedStatsCommand
  ): Vector[AdvancedStatsRecomputeTask] =
    riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findPending(connection, command.limit, command.processedAt).flatMap { task =>
      val maybeProcessing =
        try
          Some(
            riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(
              connection,
              AdvancedStatsRecomputeTaskFunctions.markProcessing(task, command.processedAt)
            )
          )
        catch
          case _: OptimisticConcurrencyException =>
            None

      maybeProcessing.map { processing =>
        try
          processing.owner match
            case DashboardOwner.Player(playerId) =>
              riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.save(connection, rebuildPlayerBoard(connection, playerId, command.processedAt))
            case DashboardOwner.Club(clubId) =>
              val club = ResolveClubPrivateAPIMessage(clubId).plan(ApiPlanContext(bearerToken = None, connection = connection)).unsafeRunSync().getOrElse(
                throw NoSuchElementException(s"Club ${clubId.value} was not found")
              )
              riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.save(connection, rebuildClubBoard(connection, club, command.processedAt))

          try
            riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(
              connection,
              AdvancedStatsRecomputeTaskFunctions.markCompleted(processing, command.processedAt)
            )
          catch
            case _: OptimisticConcurrencyException =>
              riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findById(connection, processing.id).getOrElse(processing)
        catch
          case _: OptimisticConcurrencyException =>
            riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findById(connection, processing.id).getOrElse(processing)
          case error: Throwable =>
            val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            if processing.attempts >= maxAttempts then
              riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(
                connection,
                AdvancedStatsRecomputeTaskFunctions.markDeadLetter(processing, errorMessage, command.processedAt)
              )
            else
              val retryAt = command.processedAt.plus(retryDelay)
              riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(connection, 
                AdvancedStatsRecomputeTaskFunctions.markRetryScheduled(
                  processing,
                  errorMessage,
                  command.processedAt,
                  retryAt
                )
              )
      }
    }

  private def rebuildPlayerBoard(
      connection: java.sql.Connection,
      playerId: PlayerId,
      at: Instant
  ): AdvancedStatsBoard =
    val records = ListPlayerMatchRecordsPrivateAPIMessage(playerId)
      .plan(ApiPlanContext(bearerToken = None, connection = connection))
      .unsafeRunSync()
    val paifus = ListPlayerPaifusPrivateAPIMessage(playerId)
      .plan(ApiPlanContext(bearerToken = None, connection = connection))
      .unsafeRunSync()
    val existingVersion =
      riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
    AdvancedStatsBoardFunctions.buildPlayerBoard(playerId, records, paifus, at, existingVersion)

  private def rebuildClubBoard(
      connection: java.sql.Connection,
      club: Club,
      at: Instant
  ): AdvancedStatsBoard =
    val memberBoards = club.members.flatMap { playerId =>
      PlayerPersistenceFunctions.findPlayer(connection, playerId)
        .filter(_.status == PlayerStatus.Active)
        .map(_ => rebuildPlayerBoard(connection, playerId, at))
    }
    val existingVersion =
      riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    AdvancedStatsBoardFunctions.buildClubBoard(club, memberBoards, at, existingVersion)

  private final case class ProcessAdvancedStatsCommand(
      operator: AccessPrincipal,
      limit: Int,
      processedAt: Instant
  )
