package riichinexus.microservices.auth.api.session

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.auth.objects.session.GuestSessionId
import riichinexus.microservices.auth.domain.session.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.session.model.GuestAccessSession

import riichinexus.microservices.player.objects.PlayerStatus

import riichinexus.microservices.auth.objects.session.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
/** 将游客会话升级绑定到玩家。 */
final case class UpgradeGuestSessionAuthAPIMessage(
    sessionId: String,
    playerId: String
) extends APIMessage[GuestSessionResponse]:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      upgradedAt <- IO.realTimeInstant
      resolvedSessionId = GuestSessionId(sessionId)
      resolvedPlayerId = PlayerId(playerId)
      savedSession <- upgradeGuestSession(context, resolvedSessionId, resolvedPlayerId, upgradedAt)
      _ <- RecordAuditEventsPrivateAPIMessage(upgradeGuestSessionAudit(savedSession, resolvedPlayerId, upgradedAt)).plan(context)
    yield guestSessionResponse(savedSession)

  private def upgradeGuestSession(
      context: ApiPlanContext,
      sessionId: GuestSessionId,
      playerId: PlayerId,
      upgradedAt: Instant
  ): IO[GuestAccessSession] =
    val connection = context.connection
    for
      session <- IO.blocking(
        GuestSessionTable.findById(connection, sessionId)
          .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
      )
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context).map(
        _.getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
      )
      savedSession <- IO.blocking {
        require(
          player.status == PlayerStatus.Active,
          s"Player ${playerId.value} must be active before linking a guest session"
        )
        GuestSessionTable.save(
          connection,
          GuestAccessSessionFunctions.upgrade(session, playerId, upgradedAt)
        )
      }
    yield savedSession

  private def upgradeGuestSessionAudit(
      savedSession: GuestAccessSession,
      playerId: PlayerId,
      upgradedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.GuestSession,
        aggregateId = savedSession.id.value,
        eventType = AuditEventType.GuestSessionUpgraded,
        occurredAt = upgradedAt,
        actorId = Some(playerId),
        details = Map(StructuredEventField.toString(StructuredEventField.PlayerId) -> playerId.value),
        note = None
      )
    )

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )

