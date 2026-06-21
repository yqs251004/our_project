package riichinexus.microservices.club.objects.membershipmanagement

/** 入会申请在审核流程中的状态。
  *
  * 它覆盖待审、通过、拒绝和申请人撤回四个终态/中间态，供成员中心和俱乐部管理页保持一致展示。
  */
enum ClubApplicationStatus:
  case Pending
  case Approved
  case Rejected
  case Withdrawn

object ClubApplicationStatus:

  def toString(status: ClubApplicationStatus): String =
    status match
      case ClubApplicationStatus.Pending => "Pending"
      case ClubApplicationStatus.Approved => "Approved"
      case ClubApplicationStatus.Rejected => "Rejected"
      case ClubApplicationStatus.Withdrawn => "Withdrawn"

  def fromString(value: String): ClubApplicationStatus =
    value.trim match
      case "Pending" => ClubApplicationStatus.Pending
      case "Approved" => ClubApplicationStatus.Approved
      case "Rejected" => ClubApplicationStatus.Rejected
      case "Withdrawn" => ClubApplicationStatus.Withdrawn
      case other => throw IllegalArgumentException(s"Unsupported ClubApplicationStatus value: $other")
