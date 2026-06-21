package riichinexus.microservices.tournament.objects.paifu

/** 一小局最终结算的结果类型。
  *
  * 结果区分自摸、荣和、荒牌流局和途中流局，决定分数变化、听牌信息和结算提示如何解释。
  */
enum HandOutcome:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

object HandOutcome:
  def toString(outcome: HandOutcome): String =
    outcome.toString

  def fromString(value: String): HandOutcome =
    HandOutcome.valueOf(value)
