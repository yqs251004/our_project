package riichinexus.microservices.auth.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class UpgradeGuestSessionAuthAPIMessage(
    sessionId: String,
    playerId: String
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      upgradedAt <- IO.realTimeInstant
      module = context.support.authModule
      command = UpgradeGuestSessionCommand(
        sessionId = GuestSessionId(sessionId),
        playerId = PlayerId(playerId),
        upgradedAt = upgradedAt
      )
      session <- IO {
        module.transactionManager.inTransaction {
          upgradeGuestSession(context.connection, module, command)
        }
      }
    yield GuestSessionResponse.fromDomain(session)

  private def upgradeGuestSession(
      connection: java.sql.Connection,
      module: AuthModuleContext,
      command: UpgradeGuestSessionCommand
  ): GuestAccessSession =
    val session = GuestSessionTable.findById(connection, command.sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${command.sessionId.value} was not found"))
    val player = PlayerTable
      .findById(connection, command.playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found"))
    require(
      player.status == PlayerStatus.Active,
      s"Player ${command.playerId.value} must be active before linking a guest session"
    )

    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitWithinTransaction(
        DomainChange(
          aggregate = session.upgrade(command.playerId, command.upgradedAt),
          persist = upgradedSession =>
            val savedSession = GuestSessionTable.save(connection, upgradedSession)
            reconcileGuestApplications(connection, command.sessionId, player)
            savedSession,
          auditEntries = savedSession =>
            Vector(
              AuditEventEntry(
                id = IdGenerator.auditEventId(),
                aggregateType = "guest-session",
                aggregateId = savedSession.id.value,
                eventType = "GuestSessionUpgraded",
                occurredAt = command.upgradedAt,
                actorId = Some(command.playerId),
                details = Map("playerId" -> command.playerId.value),
                note = None
              )
            )
        )
      )

  private def reconcileGuestApplications(
      connection: java.sql.Connection,
      sessionId: GuestSessionId,
      player: Player
  ): Unit =
    val guestApplicantId = s"guest:${sessionId.value}"
    ClubTable.findAll(connection).foreach { club =>
      val updatedApplications = club.membershipApplications.map { application =>
        if application.isPending && application.applicantUserId.contains(guestApplicantId) then
          application.bindRegisteredApplicant(player.userId, player.nickname)
        else application
      }

      if updatedApplications != club.membershipApplications then
        ClubTable.save(connection, club.copy(membershipApplications = updatedApplications))
    }

  private final case class UpgradeGuestSessionCommand(
      sessionId: GuestSessionId,
      playerId: PlayerId,
      upgradedAt: Instant
  )
