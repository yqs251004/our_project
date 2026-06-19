package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

/** 描述一张比赛桌绑定的日本麻将实时对局当前处于哪个生命周期阶段。 */
enum MahjongTableStatus:
  case NotStarted
  case InProgress
  case WaitingPlayerAction
  case WaitingCallDecision
  case RoundEnded
  case Finished
  case Aborted
  case Archived

object MahjongTableStatus:
  def toString(status: MahjongTableStatus): String =
    status.toString

  def fromString(value: String): MahjongTableStatus =
    MahjongTableStatus.valueOf(value)
