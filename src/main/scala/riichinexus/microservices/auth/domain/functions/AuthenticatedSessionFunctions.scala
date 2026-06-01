package riichinexus.microservices.auth.domain.functions

import java.security.SecureRandom
import java.time.{Duration, Instant}
import java.util.Base64

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.auth.domain.model.AuthenticatedSession

object AuthenticatedSessionFunctions:
  private val random = SecureRandom()

  def isExpired(session: AuthenticatedSession, asOf: Instant = Instant.now()): Boolean =
    !session.expiresAt.isAfter(asOf)

  def isRevoked(session: AuthenticatedSession): Boolean =
    session.revokedAt.nonEmpty

  def canAuthenticate(session: AuthenticatedSession, asOf: Instant = Instant.now()): Boolean =
    !isRevoked(session) && !isExpired(session, asOf)

  def touch(session: AuthenticatedSession, at: Instant): AuthenticatedSession =
    session.copy(
      lastSeenAt = Some(
        session.lastSeenAt match
          case Some(existing) if existing.isAfter(at) => existing
          case _                                      => at
      )
    )

  def revoke(session: AuthenticatedSession, at: Instant): AuthenticatedSession =
    session.copy(revokedAt = Some(at))

  def create(
      username: String,
      playerId: PlayerId,
      createdAt: Instant = Instant.now(),
      ttl: Duration = Duration.ofDays(30)
  ): AuthenticatedSession =
    require(!ttl.isNegative && !ttl.isZero, "Authenticated session TTL must be positive")
    AuthenticatedSession(
      token = nextToken(),
      username = AccountCredentialFunctions.normalizeUsername(username),
      playerId = playerId,
      createdAt = createdAt,
      expiresAt = createdAt.plus(ttl)
    )

  def validate(session: AuthenticatedSession): Unit =
    require(session.token.trim.nonEmpty, "Authenticated session token cannot be empty")
    require(session.username == AccountCredentialFunctions.normalizeUsername(session.username), "Authenticated session username must be normalized")
    require(!session.expiresAt.isBefore(session.createdAt), "Authenticated session expiry cannot be before creation")

  private def nextToken(): String =
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
