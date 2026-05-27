package riichinexus.microservices.auth.domain

final case class AuthorizationFailure(message: String) extends RuntimeException(message)
