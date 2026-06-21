package riichinexus.microservices.tournament.objects.paifu

/** 牌谱时间线中可记录的动作类型。
  *
  * 类型覆盖摸切、副露、杠、立直、宝牌翻开、和牌和流局，供回放、统计和动画按事件类型分发。
  */
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
