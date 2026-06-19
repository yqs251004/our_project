package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ListGuestSessionsRequest 表示列表游客Sessions请求 的前端请求参数。 */

final case class ListGuestSessionsRequest(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ListGuestSessionsRequest:
  given ReadWriter[ListGuestSessionsRequest] = macroRW
