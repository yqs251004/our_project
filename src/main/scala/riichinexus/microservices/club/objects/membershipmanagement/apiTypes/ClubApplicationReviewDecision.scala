package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

/** 管理员审核入会申请时可提交的裁定。
  *
  * 当前只允许通过或拒绝；撤回由申请人走独立接口，避免把申请人动作和审核动作混在一起。
  */
enum ClubApplicationReviewDecision:
  case Approve
  case Reject

object ClubApplicationReviewDecision:
  def toString(decision: ClubApplicationReviewDecision): String =
    decision match
      case Approve => "approve"
      case Reject  => "reject"

  def fromString(value: String): ClubApplicationReviewDecision =
    value match
      case "approve" => Approve
      case "reject"  => Reject
      case other     => throw IllegalArgumentException(s"Unknown club application review decision $other")
