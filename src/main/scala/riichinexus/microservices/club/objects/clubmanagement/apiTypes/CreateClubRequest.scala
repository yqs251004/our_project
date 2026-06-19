package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** CreateClubRequest 表示创建俱乐部请求 的前端请求参数。 */

final case class CreateClubRequest(
    name: String,
    creatorId: String
)

object CreateClubRequest:
  given ReadWriter[CreateClubRequest] = macroRW
