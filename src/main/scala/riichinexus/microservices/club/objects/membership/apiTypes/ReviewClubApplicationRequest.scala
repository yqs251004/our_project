package riichinexus.microservices.club.objects.membership.apiTypes

import riichinexus.microservices.club.objects.membership.ClubApplicationReviewDecision
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 俱乐部管理员审核入会申请时提交的请求体。
  *
  * `operatorId` 是审核人，`decision` 决定通过或拒绝，`note` 会作为申请历史的一部分返回给相关页面。
  */
final case class ReviewClubApplicationRequest(
    operatorId: String,
    decision: ClubApplicationReviewDecision,
    note: Option[String] = None
)

object ReviewClubApplicationRequest:
  given ReadWriter[ReviewClubApplicationRequest] = macroRW
