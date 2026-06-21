package riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 覆盖俱乐部等级树的管理请求。
  *
  * `ranks` 表示新的完整等级结构，后端会按操作者和备注生成审计记录，并重新计算受影响成员的权限快照。
  */
final case class UpdateClubRankTreeRequest(
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
)

object UpdateClubRankTreeRequest:
  given ReadWriter[UpdateClubRankTreeRequest] = macroRW
