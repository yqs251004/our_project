package riichinexus.microservices.club.objects.profile.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 创建俱乐部时由大厅提交的最小请求体。
  *
  * `creatorId` 会成为初始管理员和成员来源，俱乐部资产、等级树与关系则由后端初始化。
  */
final case class CreateClubRequest(
    name: String,
    creatorId: String
)

object CreateClubRequest:
  given ReadWriter[CreateClubRequest] = macroRW
