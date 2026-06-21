package riichinexus.microservices.auth.domain

/** 认证流程中可被上层 API 转换为失败响应的异常。
  *
  * `message` 面向日志和调用方解释失败原因，`code` 保留稳定错误码，避免前端依赖可变的自然语言文案。
  */
final case class AuthenticationFailure(
    message: String,
    code: String = "authentication_failed"
) extends RuntimeException(message)
