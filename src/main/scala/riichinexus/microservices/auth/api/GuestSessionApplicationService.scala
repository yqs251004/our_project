package riichinexus.microservices.auth.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*

final class GuestSessionApplicationService(
    playerRepository: PlayerRepository,
    guestSessionRepository: GuestSessionRepository,
    clubRepository: ClubRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def createSession(
      displayName: String = "guest",
      createdAt: Instant = Instant.now(),
      ttl: java.time.Duration = java.time.Duration.ofDays(30),
      deviceFingerprint: Option[String] = None
  ): GuestAccessSession =
    transactionManager.inTransaction {
      val normalizedDisplayName =
        Option(displayName).map(_.trim).filter(_.nonEmpty).getOrElse("guest")

      val savedSession = guestSessionRepository.save(
        GuestAccessSession.create(
          id = IdGenerator.guestSessionId(),
          createdAt = createdAt,
          displayName = normalizedDisplayName,
          ttl = ttl,
          deviceFingerprint = deviceFingerprint
        )
      )
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "guest-session",
          aggregateId = savedSession.id.value,
          eventType = "GuestSessionCreated",
          occurredAt = createdAt,
          details = Map(
            "expiresAt" -> savedSession.expiresAt.toString,
            "deviceFingerprint" -> savedSession.deviceFingerprint.getOrElse("none")
          )
        )
      )
      savedSession
    }

  def findSession(sessionId: GuestSessionId): Option[GuestAccessSession] =
    guestSessionRepository.findById(sessionId)

  def findActiveSession(
      sessionId: GuestSessionId,
      asOf: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    guestSessionRepository.findById(sessionId).filter(_.canAuthenticate(asOf))

  def touchActiveSession(
      sessionId: GuestSessionId,
      seenAt: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    transactionManager.inTransaction {
      guestSessionRepository.findById(sessionId).map { session =>
        require(session.canAuthenticate(seenAt), inactiveSessionMessage(session, seenAt))
        guestSessionRepository.save(session.touch(seenAt))
      }
    }

  def revokeSession(
      sessionId: GuestSessionId,
      reason: String,
      revokedAt: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    transactionManager.inTransaction {
      guestSessionRepository.findById(sessionId).map { session =>
        val updated = guestSessionRepository.save(session.revoke(reason, revokedAt))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "guest-session",
            aggregateId = sessionId.value,
            eventType = "GuestSessionRevoked",
            occurredAt = revokedAt,
            details = Map("reason" -> updated.revokedReason.getOrElse(reason))
          )
        )
        updated
      }
    }

  def upgradeSession(
      sessionId: GuestSessionId,
      playerId: PlayerId,
      upgradedAt: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    transactionManager.inTransaction {
      guestSessionRepository.findById(sessionId).map { session =>
        val player = playerRepository
          .findById(playerId)
          .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
        require(
          player.status == PlayerStatus.Active,
          s"Player ${playerId.value} must be active before linking a guest session"
        )

        val updated = guestSessionRepository.save(session.upgrade(playerId, upgradedAt))
        reconcileGuestApplications(session.id, player)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "guest-session",
            aggregateId = sessionId.value,
            eventType = "GuestSessionUpgraded",
            occurredAt = upgradedAt,
            actorId = Some(playerId),
            details = Map("playerId" -> playerId.value)
          )
        )
        updated
      }
    }

  private def reconcileGuestApplications(
      sessionId: GuestSessionId,
      player: Player
  ): Unit =
    val guestApplicantId = s"guest:${sessionId.value}"
    clubRepository.findAll().foreach { club =>
      val updatedApplications = club.membershipApplications.map { application =>
        if application.isPending && application.applicantUserId.contains(guestApplicantId) then
          application.bindRegisteredApplicant(player.userId, player.nickname)
        else application
      }

      if updatedApplications != club.membershipApplications then
        clubRepository.save(club.copy(membershipApplications = updatedApplications))
    }

  private def inactiveSessionMessage(session: GuestAccessSession, at: Instant): String =
    if session.isRevoked then
      s"Guest session ${session.id.value} has been revoked"
    else if session.isUpgraded then
      s"Guest session ${session.id.value} has already been upgraded to player access"
    else if session.isExpired(at) then
      s"Guest session ${session.id.value} expired at ${session.expiresAt}"
    else
      s"Guest session ${session.id.value} cannot be used for authentication"
