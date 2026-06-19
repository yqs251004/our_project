package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AdjustClubMemberContributionRequest 表示Adjust俱乐部成员贡献请求 的前端请求参数。 */

final case class AdjustClubMemberContributionRequest(
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
)

object AdjustClubMemberContributionRequest:
  given ReadWriter[AdjustClubMemberContributionRequest] = macroRW
