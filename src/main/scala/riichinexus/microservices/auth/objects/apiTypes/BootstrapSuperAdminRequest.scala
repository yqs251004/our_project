package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** BootstrapSuperAdminRequest 表示初始化超级管理员请求 的前端请求参数。 */

final case class BootstrapSuperAdminRequest(
    bootstrapKey: String,
    username: String,
    password: String,
    displayName: String
)

object BootstrapSuperAdminRequest:
  given ReadWriter[BootstrapSuperAdminRequest] = macroRW
