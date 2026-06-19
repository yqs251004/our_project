package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifu.{AgariResult, AgariWinResult, MahjongYakuKind, Yaku}
import riichinexus.microservices.tournament.objects.paifu.HandOutcome

/** AgariResultFunctions 提供Agari结果相关的领域计算、校验和转换函数。 */

private[tournament] object AgariResultFunctions:
  def validate(result: AgariResult): Unit =
    require(result.points >= 0, "Result points must be non-negative")
    require(result.scoreChanges.nonEmpty, "Result must include score changes")
    require(
      result.scoreChanges.map(_.playerId).distinct.size == result.scoreChanges.size,
      "Score changes cannot contain duplicate players"
    )
    result.doraIndicators.foreach { indicators =>
      require(indicators.size == 5, "Dora indicators must contain exactly five tiles when provided")
      PaifuTileFunctions.validateAll(indicators, "Dora indicators")
    }
    result.uraDoraIndicators.foreach { indicators =>
      require(indicators.size == 5, "Ura-dora indicators must contain exactly five tiles when provided")
      PaifuTileFunctions.validateAll(indicators, "Ura-dora indicators")
    }
    val wins = effectiveWins(result)
    result.outcome match
      case HandOutcome.Ron =>
        require(result.winner.nonEmpty, "Ron result must include a winner")
        require(result.target.nonEmpty, "Ron result must include a target")
        require(wins.nonEmpty, "Ron result must include at least one win")
        wins.foreach { win =>
          require(win.target.nonEmpty, "Ron win result must include a target")
          validateWinningDetails(win.han.orElse(result.han), win.fu.orElse(result.fu), effectiveYaku(win, result))
        }
      case HandOutcome.Tsumo =>
        require(result.winner.nonEmpty, "Tsumo result must include a winner")
        require(result.target.isEmpty, "Tsumo result must not include a discard target")
        require(wins.nonEmpty, "Tsumo result must include at least one win")
        wins.foreach { win =>
          require(win.target.isEmpty, "Tsumo win result must not include a discard target")
          val yaku = effectiveYaku(win, result)
          validateWinningDetails(win.han.orElse(result.han), win.fu.orElse(result.fu), yaku, allowMissingFu = hasNagashiMangan(yaku))
        }
      case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
        require(result.winner.isEmpty, "Drawn hands cannot include a winner")
        require(result.target.isEmpty, "Drawn hands cannot include a target")
        require(result.han.isEmpty && result.fu.isEmpty, "Drawn hands cannot include han/fu")
        require(result.yaku.isEmpty, "Drawn hands cannot include yaku")

  private def effectiveWins(result: AgariResult): Vector[AgariWinResult] =
    if result.wins.nonEmpty then result.wins
    else
      result.winner.toVector.map { winner =>
        AgariWinResult(
          winner = winner,
          target = result.target,
          han = result.han,
          fu = result.fu,
          yaku = result.yaku,
          points = result.points,
          doraIndicators = result.doraIndicators,
          uraDoraIndicators = result.uraDoraIndicators,
          uraDoraVisible = result.uraDoraVisible
        )
      }

  private def effectiveYaku(win: AgariWinResult, result: AgariResult): Vector[Yaku] =
    if win.yaku.nonEmpty then win.yaku else result.yaku

  private def validateWinningDetails(
      han: Option[Int],
      fu: Option[Int],
      yaku: Vector[Yaku],
      allowMissingFu: Boolean = false
  ): Unit =
    require(han.nonEmpty && (fu.nonEmpty || allowMissingFu), "Winning hands must include han and fu")
    require(yaku.nonEmpty, "Winning hands must include at least one yaku")

  private def hasNagashiMangan(yaku: Vector[Yaku]): Boolean =
    yaku.exists(_.kind == MahjongYakuKind.NagashiMangan)
