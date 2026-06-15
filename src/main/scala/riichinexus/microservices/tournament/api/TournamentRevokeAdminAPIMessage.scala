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

final case class TournamentRevokeAdminAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      revokedAt <- IO.realTimeInstant
      command = RevokeTournamentAdminCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(playerId),
        actor = actor,
        revokedAt = revokedAt
      )
      savedTournament <- revokeAdmin(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(revokeAdminAudit(command)).plan(context)
    yield TournamentSummaryView.fromDomain(savedTournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipal] =
    operatorId.map(PlayerId(_))
      .map(ResolveAccessPrincipal(_).plan(context))
      .getOrElse(IO.pure(AccessPrincipalFunctions.system))

  private def revokeAdmin(
      context: ApiPlanContext,
      command: RevokeTournamentAdminCommand
  ): IO[Option[Tournament]] =
    val connection = context.connection
    for
      tournament <- IO.blocking(riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      savedTournament <- tournament match
        case None => IO.pure(None)
        case Some(tournament) =>
          ensureAdminCanBeRevoked(tournament, command)
          commitAdminRevocation(context, tournament, player, command).map(Some(_))
    yield savedTournament

  private def ensureAdminCanBeRevoked(
      tournament: Tournament,
      command: RevokeTournamentAdminCommand
  ): Unit =
    AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, 
      command.actor,
      Permission.AssignTournamentAdmin,
      tournamentId = Some(command.tournamentId)
    )
    if !tournament.admins.contains(command.playerId) then
      throw IllegalArgumentException(
        s"Player ${command.playerId.value} is not a tournament admin of tournament ${command.tournamentId.value}"
      )
    if tournament.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Tournament ${command.tournamentId.value} must retain at least one tournament admin"
      )

  private def commitAdminRevocation(
      context: ApiPlanContext,
      tournament: Tournament,
      player: Player,
      command: RevokeTournamentAdminCommand
  ): IO[Tournament] =
    val connection = context.connection
    for
      _ <- SavePlayerPrivateAPIMessage(PlayerRoleFunctions.revokeTournamentAdmin(player, command.tournamentId)).plan(context)
      savedTournament <- IO.blocking {
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(
          connection,
          tournament.copy(admins = tournament.admins.filterNot(_ == command.playerId))
        )
      }
    yield savedTournament

  private def revokeAdminAudit(command: RevokeTournamentAdminCommand): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "tournament",
        aggregateId = command.tournamentId.value,
        eventType = "TournamentAdminRevoked",
        occurredAt = command.revokedAt,
        actorId = command.actor.playerId,
        details = Map("playerId" -> command.playerId.value),
        note = Some(s"Revoked tournament admin from ${command.playerId.value}")
      )
    )

  private final case class RevokeTournamentAdminCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      revokedAt: Instant
  )

