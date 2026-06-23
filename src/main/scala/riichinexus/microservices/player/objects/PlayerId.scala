package riichinexus.microservices.player.objects

/** 玩家档案的稳定标识符。
  *
  * 它和账号 userId 分离，允许一个玩家档案在赛事、俱乐部和牌谱中作为独立业务主体被引用。
  */
final case class PlayerId(value: String)
