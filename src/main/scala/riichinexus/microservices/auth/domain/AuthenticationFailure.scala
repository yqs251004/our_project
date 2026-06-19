package riichinexus.microservices.auth.domain

/** AuthenticationFailure 表示后端领域中的认证失败状态或规则，包含消息、code。 */

final case class AuthenticationFailure(
    message: String,
    code: String = "authentication_failed"
) extends RuntimeException(message)
