package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** ReviewClubApplicationRequest 表示Review俱乐部申请请求 的前端请求参数。 */

final case class ReviewClubApplicationRequest(
    operatorId: String,
    decision: ClubApplicationReviewDecision,
    note: Option[String] = None
)

object ReviewClubApplicationRequest:
  given ReadWriter[ReviewClubApplicationRequest] = macroRW
