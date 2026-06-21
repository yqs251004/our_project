package riichinexus.microservices.tournament.api
import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerTournamentAdminRevocationPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentSummaryView

/** 撤销玩家指定赛事的管理员身份。 */
final case class TournamentRevokeAdminAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView]:

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
    yield TournamentViewFunctions.tournamentSummaryView(savedTournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def revokeAdmin(
      context: ApiPlanContext,
      command: RevokeTournamentAdminCommand
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
            _ <- IO.blocking(ensureAdminCanBeRevoked(tournament, command))
            saved <- commitAdminRevocation(context, tournament, player, command)
          yield Some(saved)
    yield savedTournament

  private def ensureAdminCanBeRevoked(
      tournament: Tournament,
      command: RevokeTournamentAdminCommand
  ): Unit =
    if !tournament.admins.contains(command.playerId) then
      throw IllegalArgumentException(
        s"PlayerPrivateView ${command.playerId.value} is not a tournament admin of tournament ${command.tournamentId.value}"
      )
    if tournament.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Tournament ${command.tournamentId.value} must retain at least one tournament admin"
      )

  private def commitAdminRevocation(
      context: ApiPlanContext,
      tournament: Tournament,
      player: PlayerPrivateView,
      command: RevokeTournamentAdminCommand
  ): IO[Tournament] =
    val connection = context.connection
    for
      _ <- RecordPlayerTournamentAdminRevocationPrivateAPIMessage(command.playerId, command.tournamentId).plan(context)
      savedTournament <- IO.blocking {
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(
          connection,
          tournament.copy(admins = tournament.admins.filterNot(_ == command.playerId))
        )
      }
    yield savedTournament

  private def revokeAdminAudit(command: RevokeTournamentAdminCommand): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "tournament",
        aggregateId = command.tournamentId.value,
        eventType = AuditEventType.TournamentAdminRevoked,
        occurredAt = command.revokedAt,
        actorId = command.actor.playerId,
        details = Map("playerId" -> command.playerId.value),
        note = Some(s"Revoked tournament admin from ${command.playerId.value}")
      )
    )

  /** 撤销赛事管理员身份时使用的已授权内部命令。 */
  private final case class RevokeTournamentAdminCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      revokedAt: Instant
  )
