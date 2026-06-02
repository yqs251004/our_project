package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import upickle.default.*

enum ClubApplicationReviewDecision:
  case Approve
  case Reject

object ClubApplicationReviewDecision:
  given ReadWriter[ClubApplicationReviewDecision] =
    readwriter[String].bimap(toString, fromString)

  def toString(decision: ClubApplicationReviewDecision): String =
    decision match
      case Approve => "approve"
      case Reject  => "reject"

  def fromString(value: String): ClubApplicationReviewDecision =
    value match
      case "approve" => Approve
      case "reject"  => Reject
      case other     => throw IllegalArgumentException(s"Unknown club application review decision $other")
