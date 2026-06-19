package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

/** 描述当前小局内部的推进阶段，用于前端判断该展示行动、鸣牌等待还是结算结果。 */
enum MahjongRoundPhase:
  case InitialDeal
  case PlayerTurn
  case CallDecision
  case WinDecision
  case Settlement
  case Finished

object MahjongRoundPhase:
  def toString(phase: MahjongRoundPhase): String =
    phase.toString

  def fromString(value: String): MahjongRoundPhase =
    MahjongRoundPhase.valueOf(value)
