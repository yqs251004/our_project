package riichinexus.microservices.club.objects

import upickle.default.*

enum ClubTournamentScope derives CanEqual:
  case Recent
  case Active
  case All

  def value: String =
    this match
      case Recent => "recent"
      case Active => "active"
      case All    => "all"

object ClubTournamentScope:
  given ReadWriter[ClubTournamentScope] =
    readwriter[String].bimap(_.value, fromValue)

  def fromValue(value: String): ClubTournamentScope =
    value match
      case "recent" => Recent
      case "active" => Active
      case "all"    => All
      case other    => throw IllegalArgumentException(s"Unknown club tournament scope $other")
