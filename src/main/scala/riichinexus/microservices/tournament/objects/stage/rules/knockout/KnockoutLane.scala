package riichinexus.microservices.tournament.objects.stage.rules.knockout

/** 淘汰赛对阵中的比赛线路。
  *
  * Championship 表示主冠军线，Bronze 表示季军战，Repechage 表示复活或败者相关线路。
  */
enum KnockoutLane:
  case Championship
  case Bronze
  case Repechage

object KnockoutLane:
  def toString(lane: KnockoutLane): String =
    lane.toString

  def fromString(value: String): KnockoutLane =
    KnockoutLane.valueOf(value)
