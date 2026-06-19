package riichinexus.system.api

final case class AuthorizationFailure(message: String) extends RuntimeException(message)
