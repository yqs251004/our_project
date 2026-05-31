package riichinexus.microservices.tournament.domain.model

import riichinexus.microservices.tournament.objects.{HandOutcome}

import java.time.Instant

import riichinexus.domain.model.*


final case class AgariResult(
    outcome: HandOutcome,
    winner: Option[PlayerId] = None,
    target: Option[PlayerId] = None,
    han: Option[Int] = None,
    fu: Option[Int] = None,
    yaku: Vector[Yaku],
    points: Int,
    scoreChanges: Vector[ScoreChange],
    doraIndicators: Option[Vector[String]] = None,
    uraDoraIndicators: Option[Vector[String]] = None,
    uraDoraVisible: Option[Boolean] = None,
    tenpaiPlayerIds: Option[Vector[PlayerId]] = None,
    settlement: Option[RoundSettlement] = None
) derives CanEqual:
  require(points >= 0, "Result points must be non-negative")
  require(scoreChanges.nonEmpty, "Result must include score changes")
  require(
    scoreChanges.map(_.playerId).distinct.size == scoreChanges.size,
    "Score changes cannot contain duplicate players"
  )
  doraIndicators.foreach { indicators =>
    require(indicators.size == 5, "Dora indicators must contain exactly five tiles when provided")
  }
  uraDoraIndicators.foreach { indicators =>
    require(indicators.size == 5, "Ura-dora indicators must contain exactly five tiles when provided")
  }
  outcome match
    case HandOutcome.Ron =>
      require(winner.nonEmpty, "Ron result must include a winner")
      require(target.nonEmpty, "Ron result must include a target")
      require(han.nonEmpty && fu.nonEmpty, "Winning hands must include han and fu")
      require(yaku.nonEmpty, "Winning hands must include at least one yaku")
    case HandOutcome.Tsumo =>
      require(winner.nonEmpty, "Tsumo result must include a winner")
      require(target.isEmpty, "Tsumo result must not include a discard target")
      require(han.nonEmpty && fu.nonEmpty, "Winning hands must include han and fu")
      require(yaku.nonEmpty, "Winning hands must include at least one yaku")
    case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
      require(winner.isEmpty, "Drawn hands cannot include a winner")
      require(target.isEmpty, "Drawn hands cannot include a target")
      require(han.isEmpty && fu.isEmpty, "Drawn hands cannot include han/fu")
      require(yaku.isEmpty, "Drawn hands cannot include yaku")

