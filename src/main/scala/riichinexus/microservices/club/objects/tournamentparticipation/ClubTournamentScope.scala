package riichinexus.microservices.club.objects.tournamentparticipation

import upickle.default.*

enum ClubTournamentScope:
  case Recent
  case Active
  case All

object ClubTournamentScope:
  given ReadWriter[ClubTournamentScope] =
    readwriter[String].bimap(toString, fromString)

  def toString(scope: ClubTournamentScope): String =
    scope match
      case Recent => "recent"
      case Active => "active"
      case All    => "all"

  def fromString(value: String): ClubTournamentScope =
    value match
      case "recent" => Recent
      case "active" => Active
      case "all"    => All
      case other    => throw IllegalArgumentException(s"Unknown club tournament scope $other")
