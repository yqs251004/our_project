package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

/** ClubApplicationPolicyView 表示俱乐部申请策略视图 的前端展示视图。 */

final case class ClubApplicationPolicyView(
    applicationsOpen: Boolean,
    requirementsText: Option[String],
    expectedReviewSlaHours: Option[Int],
    pendingApplicationCount: Int
)

object ClubApplicationPolicyView:
  given ReadWriter[ClubApplicationPolicyView] = macroRW
