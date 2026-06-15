package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

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
      operator <- ResolveAccessPrincipal(operatorId).plan(context)
      processedAt <- IO.realTimeInstant
      command = ProcessAdvancedStatsCommand(operator, limit, processedAt)
      _ <- requireOpsAdmin(context, command.operator)
      tasks <- processPending(context, command)
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
      context: ApiPlanContext,
      command: ProcessAdvancedStatsCommand
  ): IO[Vector[AdvancedStatsRecomputeTask]] =
    val connection = context.connection
    for
      pending <- IO.blocking(
        riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findPending(connection, command.limit, command.processedAt)
      )
      processed <- pending.foldLeft(IO.pure(Vector.empty[AdvancedStatsRecomputeTask])) { (previous, task) =>
        previous.flatMap(tasks => processTask(context, task, command).map(_.fold(tasks)(tasks :+ _)))
      }
    yield processed

  private def processTask(
      context: ApiPlanContext,
      task: AdvancedStatsRecomputeTask,
      command: ProcessAdvancedStatsCommand
  ): IO[Option[AdvancedStatsRecomputeTask]] =
    val connection = context.connection
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

    maybeProcessing match
      case None =>
        IO.pure(None)
      case Some(processing) =>
        processMarkedTask(context, processing, command).attempt.flatMap {
          case Right(completed) =>
            IO.pure(Some(completed))
          case Left(_: OptimisticConcurrencyException) =>
            IO.blocking(
              Some(
                riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
                  .findById(connection, processing.id)
                  .getOrElse(processing)
              )
            )
          case Left(error) =>
            val errorMessage = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            IO.blocking {
              val updatedTask =
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
              Some(updatedTask)
            }
        }

  private def processMarkedTask(
      context: ApiPlanContext,
      processing: AdvancedStatsRecomputeTask,
      command: ProcessAdvancedStatsCommand
  ): IO[AdvancedStatsRecomputeTask] =
    val connection = context.connection
    for
      _ <- processing.owner match
        case DashboardOwner.Player(playerId) =>
          rebuildPlayerBoard(context, playerId, command.processedAt).flatMap(board =>
            IO.blocking(riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.save(connection, board)).map(_ => ())
          )
        case DashboardOwner.Club(clubId) =>
          ResolveClubPrivateAPIMessage(clubId).plan(context).flatMap { clubOption =>
            val club = clubOption.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
            rebuildClubBoard(context, club, command.processedAt).flatMap(board =>
              IO.blocking(riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.save(connection, board)).map(_ => ())
            )
          }
      completed <- IO.blocking {
        try
          riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.save(
            connection,
            AdvancedStatsRecomputeTaskFunctions.markCompleted(processing, command.processedAt)
          )
        catch
          case _: OptimisticConcurrencyException =>
            riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable.findById(connection, processing.id).getOrElse(processing)
      }
    yield completed

  private def rebuildPlayerBoard(
      context: ApiPlanContext,
      playerId: PlayerId,
      at: Instant
  ): IO[AdvancedStatsBoard] =
    val connection = context.connection
    for
      records <- ListPlayerMatchRecordsPrivateAPIMessage(playerId).plan(context)
      paifus <- ListPlayerPaifusPrivateAPIMessage(playerId).plan(context)
      existingVersion <- IO.blocking {
        riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
      }
    yield AdvancedStatsBoardFunctions.buildPlayerBoard(playerId, records, paifus, at, existingVersion)

  private def rebuildClubBoard(
      context: ApiPlanContext,
      club: Club,
      at: Instant
  ): IO[AdvancedStatsBoard] =
    val connection = context.connection
    for
      activeMemberIds <- club.members.foldLeft(IO.pure(Vector.empty[PlayerId])) { (previous, playerId) =>
        previous.flatMap { playerIds =>
          ResolvePlayerPrivateAPIMessage(playerId).plan(context).map {
            case Some(player) if player.status == PlayerStatus.Active => playerIds :+ playerId
            case _                                                    => playerIds
          }
        }
      }
      memberBoards <- activeMemberIds.foldLeft(IO.pure(Vector.empty[AdvancedStatsBoard])) { (previous, playerId) =>
        previous.flatMap(boards => rebuildPlayerBoard(context, playerId, at).map(board => boards :+ board))
      }
      existingVersion <- IO.blocking {
        riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable.findByOwner(connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
      }
    yield AdvancedStatsBoardFunctions.buildClubBoard(club, memberBoards, at, existingVersion)

  private final case class ProcessAdvancedStatsCommand(
      operator: AccessPrincipal,
      limit: Int,
      processedAt: Instant
  )
