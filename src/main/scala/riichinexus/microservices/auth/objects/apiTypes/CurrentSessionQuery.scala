package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 查询当前访问身份时使用的轻量参数。
  *
  * `operatorId` 支持后台以指定操作者视角解析权限，`guestSessionId` 支持未登录大厅恢复游客身份。
  */
final case class CurrentSessionQuery(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
)

object CurrentSessionQuery:
  given ReadWriter[CurrentSessionQuery] = macroRW
