package riichinexus.microservices.tournament.api
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerTournamentAdminGrantPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

import riichinexus.microservices.tournament.objects.competition.apiTypes.{AssignTournamentAdminRequest, TournamentSummaryView}
import riichinexus.microservices.tournament.objects.competition.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.ReadWriter

/** 授予玩家指定赛事的管理员身份。 */
final case class TournamentAssignAdminAPIMessage(tournamentId: String, request: AssignTournamentAdminRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
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
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found")))
      savedTournament <- tournament match
        case None => IO.pure(None)
        case Some(tournament) =>
          for
            _ <- RequirePermissionPrivateAPIMessage(
              command.actor,
              Permission.AssignTournamentAdmin,
              tournamentId = Some(command.tournamentId)
            ).plan(context)
            _ <- IO.blocking(ensureAdminCanBeAssigned(player, command))
            saved <- commitAdminAssignment(context, tournament, player, command)
          yield Some(saved)
    yield savedTournament

  private def ensureAdminCanBeAssigned(
      player: PlayerPrivateView,
      command: AssignTournamentAdminCommand
  ): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"PlayerPrivateView ${command.playerId.value} cannot be granted tournament admin")

  private def commitAdminAssignment(
      context: ApiPlanContext,
      tournament: Tournament,
      player: PlayerPrivateView,
      command: AssignTournamentAdminCommand
  ): IO[Tournament] =
    val connection = context.connection
    for
      _ <- RecordPlayerTournamentAdminGrantPrivateAPIMessage(
        command.playerId,
        command.tournamentId,
        command.grantedAt,
        command.actor.playerId
      ).plan(context)
      savedTournament <- IO.blocking {
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(
          connection,
          TournamentFunctions.assignAdmin(tournament, command.playerId)
        )
      }
    yield savedTournament

  private def assignAdminAudit(command: AssignTournamentAdminCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
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
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
  )
