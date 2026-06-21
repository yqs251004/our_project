package riichinexus.system.api

/** 授权校验失败时抛出的领域异常。 */
final case class AuthorizationFailure(message: String) extends RuntimeException(message)
