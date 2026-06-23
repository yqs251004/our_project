package riichinexus.microservices.tournament.api.competition

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerTournamentAdminRevocationPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.competition.TournamentSummaryView

/** 撤销玩家指定赛事的管理员身份。 */
final case class TournamentRevokeAdminAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      revokedAt <- IO.realTimeInstant
      requestedTournamentId = TournamentId(tournamentId)
      requestedPlayerId = PlayerId(playerId)
      savedTournament <- revokeAdmin(context, requestedTournamentId, requestedPlayerId, actor).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(revokeAdminAudit(requestedTournamentId, requestedPlayerId, actor, revokedAt)).plan(context)
    yield TournamentViewFunctions.tournamentSummaryView(savedTournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.map(PlayerId(_))
      .map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def revokeAdmin(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): IO[Option[Tournament]] =
    val connection = context.connection
    for
      tournament <- IO.blocking(TournamentTable.findById(connection, tournamentId))
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${playerId.value} was not found")))
      savedTournament <- tournament match
        case None => IO.pure(None)
        case Some(tournament) =>
          for
            _ <- RequirePermissionPrivateAPIMessage(
              actor,
              Permission.AssignTournamentAdmin,
              tournamentId = Some(tournamentId)
            ).plan(context)
            _ <- IO.blocking(ensureAdminCanBeRevoked(tournament, tournamentId, playerId))
            saved <- commitAdminRevocation(context, tournament, player, tournamentId, playerId)
          yield Some(saved)
    yield savedTournament

  private def ensureAdminCanBeRevoked(
      tournament: Tournament,
      tournamentId: TournamentId,
      playerId: PlayerId
  ): Unit =
    if !tournament.admins.contains(playerId) then
      throw IllegalArgumentException(
        s"PlayerPrivateView ${playerId.value} is not a tournament admin of tournament ${tournamentId.value}"
      )
    if tournament.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Tournament ${tournamentId.value} must retain at least one tournament admin"
      )

  private def commitAdminRevocation(
      context: ApiPlanContext,
      tournament: Tournament,
      player: PlayerPrivateView,
      tournamentId: TournamentId,
      playerId: PlayerId
  ): IO[Tournament] =
    val connection = context.connection
    for
      _ <- RecordPlayerTournamentAdminRevocationPrivateAPIMessage(playerId, tournamentId).plan(context)
      savedTournament <- IO.blocking {
        TournamentTable.save(
          connection,
          tournament.copy(admins = tournament.admins.filterNot(_ == playerId))
        )
      }
    yield savedTournament

  private def revokeAdminAudit(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      revokedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Tournament,
        aggregateId = tournamentId.value,
        eventType = AuditEventType.TournamentAdminRevoked,
        occurredAt = revokedAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value),
        note = Some(s"Revoked tournament admin from ${playerId.value}")
      )
    )
