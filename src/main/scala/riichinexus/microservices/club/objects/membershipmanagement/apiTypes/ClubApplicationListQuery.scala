package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import upickle.default.{ReadWriter, macroRW}

/** 查询入会申请列表的过滤和分页参数。
  *
  * `operatorId` 表示查看申请收件箱的人，后端据此判断其能看哪些俱乐部申请，再按状态、玩家和显示名缩小结果。
  */
final case class ClubApplicationListQuery(
    operatorId: String,
    status: Option[ClubApplicationStatus] = None,
    playerId: Option[String] = None,
    displayName: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubApplicationListQuery:
  given ReadWriter[ClubApplicationListQuery] = macroRW
