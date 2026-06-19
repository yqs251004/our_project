package riichinexus.microservices.auth.domain.functions

import riichinexus.microservices.auth.domain.model.AccountCredential

private[auth] object AccountCredentialFunctions:
  def normalizeUsername(username: String): String =
    Option(username)
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .getOrElse(throw IllegalArgumentException("Username is required"))

  def validate(credential: AccountCredential): Unit =
    require(credential.username == normalizeUsername(credential.username), "Account username must be normalized")
    require(credential.passwordHash.trim.nonEmpty, "Account password hash cannot be empty")
    require(credential.passwordSalt.trim.nonEmpty, "Account password salt cannot be empty")
    require(credential.passwordIterations > 0, "Account password iterations must be positive")
    require(!credential.updatedAt.isBefore(credential.createdAt), "Account credential updatedAt cannot be before createdAt")
