package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import cats.effect.unsafe.implicits.global
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
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.{TournamentFunctions, TournamentStageFunctions}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.api.`private`.ResolveClubPrivateAPIMessage
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.api.`private`.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageSubmitLineupAPIMessage(tournamentId: String, stageId: String, request: SubmitStageLineupRequest) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.operatorId)).resolve(context.connection))
      command = SubmitStageLineupCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        submission = stageLineupSubmission(request),
        actor = actor
      )
      _ <- IO.blocking {
        {
          submitLineup(context.connection, command)
        }
      }
      view <- IO.blocking {
        TournamentOperationViewAssembler.mutationView(context.connection, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def submitLineup(
      connection: java.sql.Connection,
      command: SubmitStageLineupCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      val stage = requireStage(tournament, command.stageId)
      ensureNoLineupConflict(stage, command)
      val club = resolveActiveClub(connection, command.submission.clubId)
      requireClubLineupCapability(command.actor, club)
      ensureSubmitterMatchesActor(command.actor, command.submission)
      ensureClubRegistered(tournament, command)
      ensureLineupPlayersActiveMembers(connection, club, command.submission)
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, 
        TournamentFunctions.updateStage(
          tournament,
          command.stageId,
          stage => TournamentStageFunctions.submitLineup(stage, command.submission)
        )
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
    val club = ResolveClubPrivateAPIMessage(clubId)
      .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
      .unsafeRunSync()
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    ensureClubActive(club)
    club

  private def ensureSubmitterMatchesActor(actor: AccessPrincipal, submission: StageLineupSubmission): Unit =
    if !AccessPrincipalFunctions.isSuperAdmin(actor) && actor.playerId.exists(_ != submission.submittedBy) then
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

      val player = PlayerPersistenceFunctions.findPlayer(connection, playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
      if player.status != PlayerStatus.Active then
        throw IllegalArgumentException(s"Player ${playerId.value} cannot be submitted to tournament lineups")
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubLineupCapability(
      actor: AccessPrincipal,
      club: Club
  ): Unit =
    val hasBasePermission =
      AuthorizationPolicyFunctions.can(AuthorizationPolicyFunctions.strict, 
        actor,
        Permission.SubmitTournamentLineup,
        clubId = Some(club.id)
      )
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) && ClubFunctions.hasPrivilege(club, playerId, ClubPrivilegeCode.PriorityLineup)
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

  private def stageLineupSubmission(request: SubmitStageLineupRequest): StageLineupSubmission =
    StageLineupSubmission(
      id = TournamentIdGenerator.lineupSubmissionId(),
      clubId = ClubId(request.clubId),
      submittedBy = PlayerId(request.operatorId),
      submittedAt = java.time.Instant.now(),
      seats = request.seats.map(stageLineupSeat),
      note = request.note
    )

  private def stageLineupSeat(request: StageLineupSeatRequest): StageLineupSeat =
    StageLineupSeat(
      playerId = PlayerId(request.playerId),
      preferredWind = request.preferredWind.map(SeatWind.valueOf),
      reserve = request.reserve
    )
