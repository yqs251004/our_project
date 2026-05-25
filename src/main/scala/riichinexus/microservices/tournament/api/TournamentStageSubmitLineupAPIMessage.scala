package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentStageSubmitLineupAPIMessage(tournamentId: String, stageId: String, request: SubmitStageLineupRequest) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(context.support.principal(request.operator))
      module = context.support.tournamentModule
      command = SubmitStageLineupCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        submission = request.toSubmission,
        actor = actor
      )
      _ <- IO {
        module.transactionManager.inTransaction {
          submitLineup(module, command)
        }
      }
      view <- IO {
        TournamentOperationViewAssembler.mutationView(module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def submitLineup(
      module: TournamentModuleContext,
      command: SubmitStageLineupCommand
  ): Option[Tournament] =
    module.tournamentRepository.findById(command.tournamentId).map { tournament =>
      val stage = requireStage(tournament, command.stageId)
      ensureNoLineupConflict(stage, command)
      val club = resolveActiveClub(module, command.submission.clubId)
      requireClubLineupCapability(module, command.actor, club)
      ensureSubmitterMatchesActor(command.actor, command.submission)
      ensureClubRegistered(tournament, command)
      ensureLineupPlayersActiveMembers(module, club, command.submission)
      module.tournamentRepository.save(
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

  private def resolveActiveClub(module: TournamentModuleContext, clubId: ClubId): Club =
    val club = module.clubRepository
      .findById(clubId)
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
      module: TournamentModuleContext,
      club: Club,
      submission: StageLineupSubmission
  ): Unit =
    submission.seats.foreach { seat =>
      val playerId = seat.playerId
      if !club.members.contains(playerId) then
        throw IllegalArgumentException(
          s"Player ${playerId.value} is not a member of club ${submission.clubId.value}"
        )

      val player = module.playerRepository
        .findById(playerId)
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
