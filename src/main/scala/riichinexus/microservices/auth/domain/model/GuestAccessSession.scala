package riichinexus.microservices.auth.domain.model

import java.time.{Duration, Instant}

import riichinexus.domain.model.{GuestSessionId, IdGenerator, PlayerId}

final case class GuestAccessSession(
    id: GuestSessionId,
    createdAt: Instant,
    displayName: String = "guest",
    expiresAt: Instant,
    lastSeenAt: Option[Instant] = None,
    revokedAt: Option[Instant] = None,
    revokedReason: Option[String] = None,
    deviceFingerprint: Option[String] = None,
    upgradedToPlayerId: Option[PlayerId] = None,
    version: Int = 0
) derives CanEqual:
  require(displayName.trim.nonEmpty, "Guest session display name cannot be empty")
  require(!expiresAt.isBefore(createdAt), "Guest session expiry cannot be earlier than creation")

  def isRevoked: Boolean =
    revokedAt.nonEmpty

  def isExpired(asOf: Instant = Instant.now()): Boolean =
    !expiresAt.isAfter(asOf)

  def isUpgraded: Boolean =
    upgradedToPlayerId.nonEmpty

  def canAuthenticate(asOf: Instant = Instant.now()): Boolean =
    !isRevoked && !isExpired(asOf) && !isUpgraded

  def touch(at: Instant): GuestAccessSession =
    copy(
      lastSeenAt = Some(
        lastSeenAt match
          case Some(existing) if existing.isAfter(at) => existing
          case _                                      => at
      )
    )

  def revoke(reason: String, at: Instant): GuestAccessSession =
    val normalizedReason = reason.trim
    require(normalizedReason.nonEmpty, "Guest session revocation reason cannot be empty")
    copy(
      revokedAt = Some(at),
      revokedReason = Some(normalizedReason)
    )

  def upgrade(playerId: PlayerId, at: Instant): GuestAccessSession =
    copy(
      lastSeenAt = Some(
        lastSeenAt match
          case Some(existing) if existing.isAfter(at) => existing
          case _                                      => at
      ),
      upgradedToPlayerId = Some(playerId)
    )

object GuestAccessSession:
  private val DefaultTtl: Duration = Duration.ofDays(30)

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
