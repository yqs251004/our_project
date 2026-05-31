package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import upickle.default.*

enum KnockoutSeedingPolicy derives CanEqual:
  case Rating
  case Elo
  case Ranking
  case Standings

  def value: String =
    this match
      case Rating    => "rating"
      case Elo       => "elo"
      case Ranking   => "ranking"
      case Standings => "standings"

object KnockoutSeedingPolicy:
  given ReadWriter[KnockoutSeedingPolicy] =
    readwriter[String].bimap(_.value, fromValue)

  def fromValue(value: String): KnockoutSeedingPolicy =
    value match
      case "rating"    => Rating
      case "elo"       => Elo
      case "ranking"   => Ranking
      case "standings" => Standings
      case other       => throw IllegalArgumentException(s"Unknown knockout seeding policy $other")
