package riichinexus.microservices.tournament.objects.paifu

/** PaifuActionType 枚举牌谱动作类型 可使用的公开取值。 */

enum PaifuActionType:
  case Draw
  case Discard
  case Chi
  case Pon
  case Kan
  case Riichi
  case DoraReveal
  case Win
  case DrawGame
  case AddedKan
  case ClosedKan
  case OpenKan

object PaifuActionType:
  def toString(actionType: PaifuActionType): String =
    actionType.toString

  def fromString(value: String): PaifuActionType =
    PaifuActionType.valueOf(value)
