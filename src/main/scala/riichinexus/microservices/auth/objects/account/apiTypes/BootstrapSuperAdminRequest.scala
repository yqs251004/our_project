package riichinexus.microservices.auth.objects.account.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 首次部署时创建超级管理员账号的请求体。
  *
  * `bootstrapKey` 用来限制初始化入口，防止普通注册流程绕过权限体系直接产生平台管理员。
  */
final case class BootstrapSuperAdminRequest(
    bootstrapKey: String,
    username: String,
    password: String,
    displayName: String
)

object BootstrapSuperAdminRequest:
  given ReadWriter[BootstrapSuperAdminRequest] = macroRW
