package riichinexus.microservices.tournament.objects.stage.rules.swiss

/** 瑞士轮阶段生成配桌时可使用的配对策略。
  *
  * BalancedElo 倾向按强弱均衡分组，Snake 按蛇形顺序分配选手，二者都会影响每轮座位计划。
  */
enum SwissPairingMethod:
  case BalancedElo
  case Snake

object SwissPairingMethod:
  def toString(method: SwissPairingMethod): String =
    method match
      case BalancedElo => "balanced-elo"
      case Snake       => "snake"

  def fromString(value: String): SwissPairingMethod =
    value match
      case "balanced-elo" => BalancedElo
      case "snake"        => Snake
      case other          => throw IllegalArgumentException(s"Unknown swiss pairing method $other")
