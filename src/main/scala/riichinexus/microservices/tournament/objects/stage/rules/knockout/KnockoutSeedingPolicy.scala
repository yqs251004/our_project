package riichinexus.microservices.tournament.objects.stage.rules.knockout

/** 生成淘汰赛初始种子时使用的排序依据。
  *
  * 策略可以来自历史 rating、当前 Elo、排名序号或阶段 standings，决定选手进入 bracket 的初始位置。
  */
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
