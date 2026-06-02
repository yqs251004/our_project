package riichinexus.microservices.auth.security

final case class PasswordDigest(
    hash: String,
    salt: String,
    iterations: Int
)
