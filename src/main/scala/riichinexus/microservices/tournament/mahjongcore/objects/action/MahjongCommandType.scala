package riichinexus.microservices.tournament.mahjongcore.objects.action

/** 玩家可提交的交互命令类型；不同于牌谱的 PaifuActionType，它只表示玩家主动选择。 */
enum MahjongCommandType:
  case Discard
  case Chi
  case Pon
  case OpenKan
  case ClosedKan
  case AddedKan
  case Riichi
  case Ron
  case Tsumo
  case Pass
  case AbortiveDraw

object MahjongCommandType:
  def toString(commandType: MahjongCommandType): String =
    commandType.toString

  def fromString(value: String): MahjongCommandType =
    MahjongCommandType.valueOf(value)
