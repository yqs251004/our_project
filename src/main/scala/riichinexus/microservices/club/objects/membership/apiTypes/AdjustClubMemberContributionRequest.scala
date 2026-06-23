package riichinexus.microservices.club.objects.membership.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 调整某位成员俱乐部贡献值的管理请求。
  *
  * `delta` 是本次贡献增减量，后端会结合目标成员、操作者和备注更新贡献并生成审计读模型。
  */
final case class AdjustClubMemberContributionRequest(
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
)

object AdjustClubMemberContributionRequest:
  given ReadWriter[AdjustClubMemberContributionRequest] = macroRW
