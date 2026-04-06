package riichinexus.domain.model

import java.time.{Duration, Instant}
import java.util.Base64
import java.security.SecureRandom

final case class AccountCredential(
    username: String,
    playerId: PlayerId,
    passwordHash: String,
    passwordSalt: String,
    passwordIterations: Int,
    createdAt: Instant,
    updatedAt: Instant,
    version: Int = 0
) derives CanEqual:
  require(username == AccountCredential.normalizeUsername(username), "Account username must be normalized")
  require(passwordHash.trim.nonEmpty, "Account password hash cannot be empty")
  require(passwordSalt.trim.nonEmpty, "Account password salt cannot be empty")
  require(passwordIterations > 0, "Account password iterations must be positive")
  require(!updatedAt.isBefore(createdAt), "Account credential updatedAt cannot be before createdAt")

object AccountCredential:
  def normalizeUsername(username: String): String =
    Option(username)
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .getOrElse(throw IllegalArgumentException("Username is required"))

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

final case class AuthSuccessView(
    userId: PlayerId,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
) derives CanEqual

final case class AuthSessionView(
    userId: PlayerId,
    username: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags
) derives CanEqual
