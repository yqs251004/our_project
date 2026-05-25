package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.{
  AgariResult,
  FinalStanding,
  KyokuDescriptor,
  KyokuRecord,
  Paifu,
  PaifuAction,
  PaifuMetadata,
  RoundSettlement,
  ScoreChange,
  Yaku
}

final case class TournamentPaifuFinalStandingView(
    playerId: String,
    seat: String,
    finalPoints: Int,
    placement: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentPaifuFinalStandingView:
  def fromDomain(standing: FinalStanding): TournamentPaifuFinalStandingView =
    TournamentPaifuFinalStandingView(
      playerId = standing.playerId.value,
      seat = standing.seat.toString,
      finalPoints = standing.finalPoints,
      placement = standing.placement,
      uma = standing.uma,
      oka = standing.oka
    )

final case class TournamentPaifuYakuView(
    name: String,
    han: Int
) derives CanEqual

object TournamentPaifuYakuView:
  def fromDomain(yaku: Yaku): TournamentPaifuYakuView =
    TournamentPaifuYakuView(
      name = yaku.name,
      han = yaku.han
    )

final case class TournamentPaifuScoreChangeView(
    playerId: String,
    delta: Int
) derives CanEqual

object TournamentPaifuScoreChangeView:
  def fromDomain(change: ScoreChange): TournamentPaifuScoreChangeView =
    TournamentPaifuScoreChangeView(
      playerId = change.playerId.value,
      delta = change.delta
    )

final case class TournamentPaifuRoundSettlementView(
    riichiSticksDelta: Int,
    honbaPayment: Int,
    notes: Vector[String]
) derives CanEqual

object TournamentPaifuRoundSettlementView:
  def fromDomain(settlement: RoundSettlement): TournamentPaifuRoundSettlementView =
    TournamentPaifuRoundSettlementView(
      riichiSticksDelta = settlement.riichiSticksDelta,
      honbaPayment = settlement.honbaPayment,
      notes = settlement.notes
    )

final case class TournamentPaifuActionView(
    sequenceNo: Int,
    actor: Option[String],
    actionType: String,
    tile: Option[String],
    shantenAfterAction: Option[Int],
    handTilesAfterAction: Option[Vector[String]],
    revealedTiles: Vector[String],
    note: Option[String]
) derives CanEqual

object TournamentPaifuActionView:
  def fromDomain(action: PaifuAction): TournamentPaifuActionView =
    TournamentPaifuActionView(
      sequenceNo = action.sequenceNo,
      actor = action.actor.map(_.value),
      actionType = action.actionType.toString,
      tile = action.tile,
      shantenAfterAction = action.shantenAfterAction,
      handTilesAfterAction = action.handTilesAfterAction,
      revealedTiles = action.revealedTiles,
      note = action.note
    )

final case class TournamentPaifuRoundDescriptorView(
    roundWind: String,
    handNumber: Int,
    honba: Int
) derives CanEqual

object TournamentPaifuRoundDescriptorView:
  def fromDomain(descriptor: KyokuDescriptor): TournamentPaifuRoundDescriptorView =
    TournamentPaifuRoundDescriptorView(
      roundWind = descriptor.roundWind.toString,
      handNumber = descriptor.handNumber,
      honba = descriptor.honba
    )

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

final case class TournamentPaifuRoundView(
    descriptor: TournamentPaifuRoundDescriptorView,
    initialHands: Map[String, Vector[String]],
    actions: Vector[TournamentPaifuActionView],
    result: TournamentPaifuRoundResultView
) derives CanEqual

object TournamentPaifuRoundView:
  def fromDomain(round: KyokuRecord): TournamentPaifuRoundView =
    TournamentPaifuRoundView(
      descriptor = TournamentPaifuRoundDescriptorView.fromDomain(round.descriptor),
      initialHands = round.initialHands.map { case (playerId, tiles) => playerId.value -> tiles },
      actions = round.actions.map(TournamentPaifuActionView.fromDomain),
      result = TournamentPaifuRoundResultView.fromDomain(round.result)
    )

final case class TournamentPaifuMetadataView(
    recordedAt: String,
    source: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    seats: Vector[TableSeat],
    matchRecordId: Option[String]
) derives CanEqual

object TournamentPaifuMetadataView:
  def fromDomain(metadata: PaifuMetadata): TournamentPaifuMetadataView =
    TournamentPaifuMetadataView(
      recordedAt = metadata.recordedAt.toString,
      source = metadata.source,
      tableId = metadata.tableId.value,
      tournamentId = metadata.tournamentId.value,
      stageId = metadata.stageId.value,
      seats = metadata.seats.map(TableSeat.fromDomain),
      matchRecordId = metadata.matchRecordId.map(_.value)
    )

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
