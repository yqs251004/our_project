package riichinexus.system.api

/** 授权校验失败时抛出的领域异常。
  *
  * `code` 是返回给客户端的稳定错误码，避免前端依赖自然语言错误文案。
  */
final case class AuthorizationFailure(
    message: String,
    code: String = "authorization_failed"
) extends RuntimeException(message)
