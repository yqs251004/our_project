package riichinexus.microservices.auth.domain

final case class AuthenticationFailure(
    message: String,
    code: String = "authentication_failed"
) extends RuntimeException(message)
