package riichinexus.microservices.auth.objects

import java.time.Instant

import riichinexus.domain.model.PlayerId

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
