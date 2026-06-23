package riichinexus.microservices.club.objects.membership

/** 俱乐部入会申请的稳定标识符。
  *
  * 独立类型能把申请 ID 与俱乐部、玩家等其他字符串 ID 区分开，减少审核流程中的误传。
  */
final case class MembershipApplicationId(value: String)
