package riichinexus.microservices.auth.objects

import java.security.SecureRandom
import java.time.{Duration, Instant}
import java.util.Base64

import riichinexus.domain.model.PlayerId

final case class AuthenticatedSession(
    token: String,
    username: String,
    playerId: PlayerId,
    createdAt: Instant,
    expiresAt: Instant,
    lastSeenAt: Option[Instant] = None,
    revokedAt: Option[Instant] = None,
    version: Int = 0
) derives CanEqual:
  require(token.trim.nonEmpty, "Authenticated session token cannot be empty")
  require(username == AccountCredential.normalizeUsername(username), "Authenticated session username must be normalized")
  require(!expiresAt.isBefore(createdAt), "Authenticated session expiry cannot be before creation")

  def isExpired(asOf: Instant = Instant.now()): Boolean =
    !expiresAt.isAfter(asOf)

  def isRevoked: Boolean =
    revokedAt.nonEmpty

  def canAuthenticate(asOf: Instant = Instant.now()): Boolean =
    !isRevoked && !isExpired(asOf)

  def touch(at: Instant): AuthenticatedSession =
    copy(
      lastSeenAt = Some(
        lastSeenAt match
          case Some(existing) if existing.isAfter(at) => existing
          case _                                      => at
      )
    )

  def revoke(at: Instant): AuthenticatedSession =
    copy(revokedAt = Some(at))

object AuthenticatedSession:
  private val random = SecureRandom()

  def create(
      username: String,
      playerId: PlayerId,
      createdAt: Instant = Instant.now(),
      ttl: Duration = Duration.ofDays(30)
  ): AuthenticatedSession =
    require(!ttl.isNegative && !ttl.isZero, "Authenticated session TTL must be positive")
    AuthenticatedSession(
      token = nextToken(),
      username = AccountCredential.normalizeUsername(username),
      playerId = playerId,
      createdAt = createdAt,
      expiresAt = createdAt.plus(ttl)
    )

  private def nextToken(): String =
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
