package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.Paifu

final case class TournamentPaifuSummaryView(
    paifuId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    recordedAt: String,
    source: String,
    matchRecordId: Option[String],
    totalHands: Int,
    playerIds: Vector[String],
    finalStandings: Vector[TournamentPaifuFinalStandingView],
    metadata: TournamentPaifuMetadataView,
    rounds: Vector[TournamentPaifuRoundView]
) derives CanEqual

object TournamentPaifuSummaryView:
  given ReadWriter[TournamentPaifuSummaryView] = macroRW

  def fromDomain(paifu: Paifu): TournamentPaifuSummaryView =
    TournamentPaifuSummaryView(
      paifuId = paifu.id.value,
      tableId = paifu.metadata.tableId.value,
      tournamentId = paifu.metadata.tournamentId.value,
      stageId = paifu.metadata.stageId.value,
      recordedAt = paifu.metadata.recordedAt.toString,
      source = paifu.metadata.source,
      matchRecordId = paifu.metadata.matchRecordId.map(_.value),
      totalHands = paifu.totalHands,
      playerIds = paifu.playerIds.map(_.value),
      finalStandings = paifu.finalStandings.map(TournamentPaifuFinalStandingView.fromDomain),
      metadata = TournamentPaifuMetadataView.fromDomain(paifu.metadata),
      rounds = paifu.rounds.map(TournamentPaifuRoundView.fromDomain)
    )
