package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.AgariResult

final case class TournamentPaifuRoundResultView(
    outcome: String,
    winner: Option[String],
    target: Option[String],
    han: Option[Int],
    fu: Option[Int],
    yaku: Vector[TournamentPaifuYakuView],
    doraIndicators: Option[Vector[String]],
    uraDoraIndicators: Option[Vector[String]],
    uraDoraVisible: Option[Boolean],
    points: Int,
    scoreChanges: Vector[TournamentPaifuScoreChangeView],
    settlement: Option[TournamentPaifuRoundSettlementView],
    tenpaiPlayerIds: Option[Vector[String]]
) derives CanEqual

object TournamentPaifuRoundResultView:
  given ReadWriter[TournamentPaifuRoundResultView] = macroRW

  def fromDomain(result: AgariResult): TournamentPaifuRoundResultView =
    TournamentPaifuRoundResultView(
      outcome = result.outcome.toString,
      winner = result.winner.map(_.value),
      target = result.target.map(_.value),
      han = result.han,
      fu = result.fu,
      yaku = result.yaku.map(TournamentPaifuYakuView.fromDomain),
      doraIndicators = result.doraIndicators,
      uraDoraIndicators = result.uraDoraIndicators,
      uraDoraVisible = result.uraDoraVisible,
      points = result.points,
      scoreChanges = result.scoreChanges.map(TournamentPaifuScoreChangeView.fromDomain),
      settlement = result.settlement.map(TournamentPaifuRoundSettlementView.fromDomain),
      tenpaiPlayerIds = result.tenpaiPlayerIds.map(_.map(_.value))
    )
