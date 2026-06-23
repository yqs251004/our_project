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
import riichinexus.microservices.player.api.`private`.RecordPlayerTournamentAdminGrantPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

import riichinexus.microservices.tournament.objects.competition.apiTypes.{AssignTournamentAdminRequest}
import riichinexus.microservices.tournament.objects.competition.{TournamentSummaryView}
import riichinexus.microservices.tournament.objects.competition.apiTypes.AssignTournamentAdminRequest.given
/** 授予玩家指定赛事的管理员身份。 */
final case class TournamentAssignAdminAPIMessage(tournamentId: String, request: AssignTournamentAdminRequest) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      grantedAt <- IO.realTimeInstant
      requestedTournamentId = TournamentId(tournamentId)
      requestedPlayerId = PlayerId(request.playerId)
      savedTournament <- assignAdmin(context, requestedTournamentId, requestedPlayerId, actor, grantedAt)
        .map(_.getOrElse(throw NoSuchElementException("Resource not found")))
      _ <- RecordAuditEventsPrivateAPIMessage(assignAdminAudit(requestedTournamentId, requestedPlayerId, actor, grantedAt)).plan(context)
    yield TournamentViewFunctions.tournamentSummaryView(savedTournament)

  private def assignAdmin(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
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
            _ <- IO.blocking(ensureAdminCanBeAssigned(player, playerId))
            saved <- commitAdminAssignment(context, tournament, playerId, actor, grantedAt)
          yield Some(saved)
    yield savedTournament

  private def ensureAdminCanBeAssigned(
      player: PlayerPrivateView,
      playerId: PlayerId
  ): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"PlayerPrivateView ${playerId.value} cannot be granted tournament admin")

  private def commitAdminAssignment(
      context: ApiPlanContext,
      tournament: Tournament,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
  ): IO[Tournament] =
    val connection = context.connection
    for
      _ <- RecordPlayerTournamentAdminGrantPrivateAPIMessage(
        playerId,
        tournament.id,
        grantedAt,
        actor.playerId
      ).plan(context)
      savedTournament <- IO.blocking {
        TournamentTable.save(
          connection,
          TournamentFunctions.assignAdmin(tournament, playerId)
        )
      }
    yield savedTournament

  private def assignAdminAudit(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Tournament,
        aggregateId = tournamentId.value,
        eventType = AuditEventType.TournamentAdminAssigned,
        occurredAt = grantedAt,
        actorId = actor.playerId,
        details = Map(StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value),
        note = Some(s"Granted tournament admin to ${playerId.value}")
      )
    )
