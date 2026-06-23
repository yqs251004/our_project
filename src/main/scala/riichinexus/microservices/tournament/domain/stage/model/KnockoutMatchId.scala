package riichinexus.microservices.tournament.domain.stage.model

/** 淘汰赛对局在赛程树中的内部编号。
  *
  * 对局编号会写入牌桌和晋级树之间的关联字段，但格式只由后端淘汰赛构造逻辑生成。
  */
private[tournament] final case class KnockoutMatchId private (value: String)

private[tournament] object KnockoutMatchId:
  def championship(roundNumber: Int, position: Int): KnockoutMatchId =
    require(roundNumber >= 1, "Knockout championship round number must be positive")
    require(position >= 1, "Knockout championship position must be positive")
    KnockoutMatchId(s"r$roundNumber-m$position")

  def bronzeFinal: KnockoutMatchId =
    KnockoutMatchId("bronze-r1-m1")

  def repechage(roundNumber: Int, position: Int): KnockoutMatchId =
    require(roundNumber >= 1, "Knockout repechage round number must be positive")
    require(position >= 1, "Knockout repechage position must be positive")
    KnockoutMatchId(s"repechage-r$roundNumber-m$position")
