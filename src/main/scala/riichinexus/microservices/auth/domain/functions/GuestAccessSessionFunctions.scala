package riichinexus.microservices.auth.domain.functions

import java.time.{Duration, Instant}

import riichinexus.domain.model.{GuestSessionId, IdGenerator, PlayerId}
import riichinexus.microservices.auth.domain.model.GuestAccessSession

object GuestAccessSessionFunctions:
  private val DefaultTtl: Duration = Duration.ofDays(30)

  def isRevoked(session: GuestAccessSession): Boolean =
    session.revokedAt.nonEmpty

  def isExpired(session: GuestAccessSession, asOf: Instant = Instant.now()): Boolean =
    !session.expiresAt.isAfter(asOf)

  def isUpgraded(session: GuestAccessSession): Boolean =
    session.upgradedToPlayerId.nonEmpty

  def canAuthenticate(session: GuestAccessSession, asOf: Instant = Instant.now()): Boolean =
    !isRevoked(session) && !isExpired(session, asOf) && !isUpgraded(session)

  def touch(session: GuestAccessSession, at: Instant): GuestAccessSession =
    session.copy(lastSeenAt = Some(latestSeenAt(session.lastSeenAt, at)))

  def revoke(session: GuestAccessSession, reason: String, at: Instant): GuestAccessSession =
    val normalizedReason = reason.trim
    require(normalizedReason.nonEmpty, "Guest session revocation reason cannot be empty")
    session.copy(
      revokedAt = Some(at),
      revokedReason = Some(normalizedReason)
    )

  def upgrade(session: GuestAccessSession, playerId: PlayerId, at: Instant): GuestAccessSession =
    session.copy(
      lastSeenAt = Some(latestSeenAt(session.lastSeenAt, at)),
      upgradedToPlayerId = Some(playerId)
    )

  def create(
      id: GuestSessionId = IdGenerator.guestSessionId(),
      createdAt: Instant = Instant.now(),
      displayName: String = "guest",
      ttl: Duration = DefaultTtl,
      deviceFingerprint: Option[String] = None
  ): GuestAccessSession =
    require(!ttl.isNegative && !ttl.isZero, "Guest session TTL must be positive")
    GuestAccessSession(
      id = id,
      createdAt = createdAt,
      displayName = displayName.trim,
      expiresAt = createdAt.plus(ttl),
      deviceFingerprint = deviceFingerprint.map(_.trim).filter(_.nonEmpty)
    )

  def ephemeral(createdAt: Instant = Instant.now()): GuestAccessSession =
    create(createdAt = createdAt, ttl = Duration.ofMinutes(5))

  def validate(session: GuestAccessSession): Unit =
    require(session.displayName.trim.nonEmpty, "Guest session display name cannot be empty")
    require(!session.expiresAt.isBefore(session.createdAt), "Guest session expiry cannot be earlier than creation")

  private def latestSeenAt(current: Option[Instant], at: Instant): Instant =
    current match
      case Some(existing) if existing.isAfter(at) => existing
      case _                                      => at
