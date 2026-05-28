package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageSubmitLineupAPIMessage(tournamentId: String, stageId: String, request: SubmitStageLineupRequest) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO.blocking(context.principal(request.operator))
      module = context.support.tournamentModule
      command = SubmitStageLineupCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        submission = request.toSubmission,
        actor = actor
      )
      _ <- IO.blocking {
        module.transactionManager.inTransaction {
          submitLineup(context.connection, module, command)
        }
      }
      view <- IO.blocking {
        TournamentOperationViewAssembler.mutationView(context.connection, module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def submitLineup(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: SubmitStageLineupCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      val stage = requireStage(tournament, command.stageId)
      ensureNoLineupConflict(stage, command)
      val club = resolveActiveClub(connection, command.submission.clubId)
      requireClubLineupCapability(module, command.actor, club)
      ensureSubmitterMatchesActor(command.actor, command.submission)
      ensureClubRegistered(tournament, command)
      ensureLineupPlayersActiveMembers(connection, club, command.submission)
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, 
        tournament.updateStage(command.stageId, _.submitLineup(command.submission))
      )
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def ensureNoLineupConflict(stage: TournamentStage, command: SubmitStageLineupCommand): Unit =
    val submissionPlayerIds = command.submission.seats.map(_.playerId).distinct
    val conflictingPlayers = stage.lineupSubmissions
      .filterNot(_.clubId == command.submission.clubId)
      .flatMap(existing => existing.seats.map(_.playerId -> existing.clubId))
      .groupBy(_._1)
      .collect {
        case (playerId, assignments)
            if submissionPlayerIds.contains(playerId) &&
              assignments.map(_._2).distinct.nonEmpty =>
          playerId.value
      }
      .toVector

    if conflictingPlayers.nonEmpty then
      throw IllegalArgumentException(
        s"Stage ${command.stageId.value} already has player(s) assigned by another club: ${conflictingPlayers.mkString(", ")}"
      )

  private def resolveActiveClub(connection: java.sql.Connection, clubId: ClubId): Club =
    val club = riichinexus.microservices.club.tables.club.ClubTable
      .findById(connection, clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    ensureClubActive(club)
    club

  private def ensureSubmitterMatchesActor(actor: AccessPrincipal, submission: StageLineupSubmission): Unit =
    if !actor.isSuperAdmin && actor.playerId.exists(_ != submission.submittedBy) then
      throw AuthorizationFailure("Lineup submitter must match the acting principal")

  private def ensureClubRegistered(tournament: Tournament, command: SubmitStageLineupCommand): Unit =
    val isClubRegistered =
      tournament.participatingClubs.contains(command.submission.clubId)
    if !isClubRegistered then
      throw IllegalArgumentException(
        s"Club ${command.submission.clubId.value} has not accepted tournament ${command.tournamentId.value}"
      )

  private def ensureLineupPlayersActiveMembers(
      connection: java.sql.Connection,
      club: Club,
      submission: StageLineupSubmission
  ): Unit =
    submission.seats.foreach { seat =>
      val playerId = seat.playerId
      if !club.members.contains(playerId) then
        throw IllegalArgumentException(
          s"Player ${playerId.value} is not a member of club ${submission.clubId.value}"
        )

      val player = PlayerTable
        .findById(connection, playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
      if player.status != PlayerStatus.Active then
        throw IllegalArgumentException(s"Player ${playerId.value} cannot be submitted to tournament lineups")
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubLineupCapability(
      module: TournamentModuleContext,
      actor: AccessPrincipal,
      club: Club
  ): Unit =
    val hasBasePermission =
      module.authorizationService.can(
        actor,
        Permission.SubmitTournamentLineup,
        clubId = Some(club.id)
      )
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform ${Permission.SubmitTournamentLineup} for club ${club.id.value}"
      )

  private final case class SubmitStageLineupCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipal
  )
