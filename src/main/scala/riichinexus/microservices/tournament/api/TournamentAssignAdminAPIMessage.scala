package riichinexus.microservices.tournament.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.util.NoSuchElementException
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
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentAssignAdminAPIMessage(tournamentId: String, request: AssignTournamentAdminRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- ResolveAccessPrincipal(PlayerId(request.operatorId)).plan(context)
      grantedAt <- IO.realTimeInstant
      command = AssignTournamentAdminCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(request.playerId),
        actor = actor,
        grantedAt = grantedAt
      )
      savedTournament <- assignAdmin(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(assignAdminAudit(command)).plan(context)
    yield TournamentSummaryView.fromDomain(savedTournament)

  private def assignAdmin(
      context: ApiPlanContext,
      command: AssignTournamentAdminCommand
  ): IO[Option[Tournament]] =
    val connection = context.connection
    for
      tournament <- IO.blocking(riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      savedTournament <- tournament match
        case None => IO.pure(None)
        case Some(tournament) =>
          ensureAdminCanBeAssigned(player, command)
          commitAdminAssignment(context, tournament, player, command).map(Some(_))
    yield savedTournament

  private def ensureAdminCanBeAssigned(
      player: Player,
      command: AssignTournamentAdminCommand
  ): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"Player ${command.playerId.value} cannot be granted tournament admin")
    AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, 
      command.actor,
      Permission.AssignTournamentAdmin,
      tournamentId = Some(command.tournamentId)
    )

  private def commitAdminAssignment(
      context: ApiPlanContext,
      tournament: Tournament,
      player: Player,
      command: AssignTournamentAdminCommand
  ): IO[Tournament] =
    val connection = context.connection
    for
      _ <- SavePlayerPrivateAPIMessage(
        PlayerRoleFunctions.grantRole(
          player,
          RoleGrantFunctions.tournamentAdmin(command.tournamentId, command.grantedAt, command.actor.playerId)
        )
      ).plan(context)
      savedTournament <- IO.blocking {
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(
          connection,
          TournamentFunctions.assignAdmin(tournament, command.playerId)
        )
      }
    yield savedTournament

  private def assignAdminAudit(command: AssignTournamentAdminCommand): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "tournament",
        aggregateId = command.tournamentId.value,
        eventType = "TournamentAdminAssigned",
        occurredAt = command.grantedAt,
        actorId = command.actor.playerId,
        details = Map("playerId" -> command.playerId.value),
        note = Some(s"Granted tournament admin to ${command.playerId.value}")
      )
    )

  private final case class AssignTournamentAdminCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant
  )
