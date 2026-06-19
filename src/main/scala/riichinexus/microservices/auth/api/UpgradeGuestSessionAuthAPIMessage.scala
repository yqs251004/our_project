package riichinexus.microservices.auth.api
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.auth.domain.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.model.GuestAccessSession

import riichinexus.microservices.player.objects.PlayerStatus

import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
/** 将游客会话升级绑定到玩家。 */
final case class UpgradeGuestSessionAuthAPIMessage(
    sessionId: String,
    playerId: String
) extends APIMessage[GuestSessionResponse]:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      upgradedAt <- IO.realTimeInstant
      command = UpgradeGuestSessionCommand(
        sessionId = GuestSessionId(sessionId),
        playerId = PlayerId(playerId),
        upgradedAt = upgradedAt
      )
      savedSession <- upgradeGuestSession(context, command)
      _ <- RecordAuditEventsPrivateAPIMessage(upgradeGuestSessionAudit(savedSession, command)).plan(context)
    yield guestSessionResponse(savedSession)

  private def upgradeGuestSession(
      context: ApiPlanContext,
      command: UpgradeGuestSessionCommand
  ): IO[GuestAccessSession] =
    val connection = context.connection
    for
      session <- IO.blocking(
        GuestSessionTable.findById(connection, command.sessionId)
          .getOrElse(throw NoSuchElementException(s"Guest session ${command.sessionId.value} was not found"))
      )
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context).map(
        _.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
      )
      savedSession <- IO.blocking {
        require(
          player.status == PlayerStatus.Active,
          s"Player ${command.playerId.value} must be active before linking a guest session"
        )
        GuestSessionTable.save(
          connection,
          GuestAccessSessionFunctions.upgrade(session, command.playerId, command.upgradedAt)
        )
      }
    yield savedSession

  private def upgradeGuestSessionAudit(
      savedSession: GuestAccessSession,
      command: UpgradeGuestSessionCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "guest-session",
        aggregateId = savedSession.id.value,
        eventType = AuditEventType.GuestSessionUpgraded,
        occurredAt = command.upgradedAt,
        actorId = Some(command.playerId),
        details = Map("playerId" -> command.playerId.value),
        note = None
      )
    )

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )

  private final case class UpgradeGuestSessionCommand(
      sessionId: GuestSessionId,
      playerId: PlayerId,
      upgradedAt: Instant
  )
