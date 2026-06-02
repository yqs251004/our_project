package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import upickle.default.*

/** 描述当前小局内部的推进阶段，用于前端判断该展示行动、鸣牌等待还是结算结果。 */
enum MahjongRoundPhase:
  case InitialDeal
  case PlayerTurn
  case CallDecision
  case WinDecision
  case Settlement
  case Finished

object MahjongRoundPhase:
  given ReadWriter[MahjongRoundPhase] =
    readwriter[String].bimap(_.toString, MahjongRoundPhase.valueOf)
