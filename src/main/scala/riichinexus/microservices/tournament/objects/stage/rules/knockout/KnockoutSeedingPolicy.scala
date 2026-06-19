package riichinexus.microservices.tournament.objects.stage.rules.knockout

/** KnockoutSeedingPolicy 枚举KnockoutSeeding策略 可使用的公开取值。 */

enum KnockoutSeedingPolicy:
  case Rating
  case Elo
  case Ranking
  case Standings

object KnockoutSeedingPolicy:
  def toString(policy: KnockoutSeedingPolicy): String =
    policy match
      case Rating    => "rating"
      case Elo       => "elo"
      case Ranking   => "ranking"
      case Standings => "standings"

  def fromString(value: String): KnockoutSeedingPolicy =
    value match
      case "rating"    => Rating
      case "elo"       => Elo
      case "ranking"   => Ranking
      case "standings" => Standings
      case other       => throw IllegalArgumentException(s"Unknown knockout seeding policy $other")
