package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** CurrentSessionQuery 表示当前会话查询 的列表或详情查询条件。 */

final case class CurrentSessionQuery(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
)

object CurrentSessionQuery:
  given ReadWriter[CurrentSessionQuery] = macroRW
