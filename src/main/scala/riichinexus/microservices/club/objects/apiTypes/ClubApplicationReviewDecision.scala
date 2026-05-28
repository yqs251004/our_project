package riichinexus.microservices.club.objects.apiTypes

import upickle.default.*

enum ClubApplicationReviewDecision derives CanEqual:
  case Approve
  case Reject

  def value: String =
    this match
      case Approve => "approve"
      case Reject  => "reject"

object ClubApplicationReviewDecision:
  given ReadWriter[ClubApplicationReviewDecision] =
    readwriter[String].bimap(_.value, fromValue)

  def fromValue(value: String): ClubApplicationReviewDecision =
    value match
      case "approve" => Approve
      case "reject"  => Reject
      case other     => throw IllegalArgumentException(s"Unknown club application review decision $other")
