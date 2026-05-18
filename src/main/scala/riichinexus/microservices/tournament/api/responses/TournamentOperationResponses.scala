package riichinexus.microservices.tournament.api.responses

import java.time.Instant

import riichinexus.domain.model.*

final case class TournamentStageDirectoryEntry(
    stageId: TournamentStageId,
    name: String,
    format: StageFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int
) derives CanEqual

final case class TournamentParticipantClubView(
    clubId: ClubId,
    clubName: String,
    memberCount: Int,
    activeMemberCount: Int
) derives CanEqual

final case class TournamentParticipantPlayerView(
    playerId: PlayerId,
    nickname: String,
    status: PlayerStatus,
    elo: Int,
    currentRank: RankSnapshot,
    clubIds: Vector[ClubId]
) derives CanEqual

final case class TournamentWhitelistSummaryView(
    totalEntries: Int,
    clubCount: Int,
    playerCount: Int,
    clubIds: Vector[ClubId],
    playerIds: Vector[PlayerId]
) derives CanEqual

final case class TournamentLineupSubmissionView(
    submissionId: LineupSubmissionId,
    clubId: ClubId,
    clubName: String,
    submittedBy: PlayerId,
    submittedByDisplayName: Option[String],
    submittedAt: Instant,
    activePlayerIds: Vector[PlayerId],
    reservePlayerIds: Vector[PlayerId],
    note: Option[String]
) derives CanEqual

final case class TournamentOperationsStageView(
    stageId: TournamentStageId,
    name: String,
    format: StageFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int,
    lineupSubmissions: Vector[TournamentLineupSubmissionView]
) derives CanEqual

final case class TournamentDetailView(
    tournamentId: TournamentId,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[TournamentParticipantClubView],
    participatingPlayers: Vector[TournamentParticipantPlayerView],
    whitelistSummary: TournamentWhitelistSummaryView,
    stages: Vector[TournamentOperationsStageView]
) derives CanEqual



final case class TournamentStageSummaryView(
    stageId: TournamentStageId,
    name: String,
    format: StageFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int
) derives CanEqual

object TournamentStageSummaryView:
  def fromDomain(stage: TournamentStage): TournamentStageSummaryView =
    TournamentStageSummaryView(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )

final case class TournamentSummaryView(
    tournamentId: TournamentId,
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    status: TournamentStatus,
    participatingClubIds: Vector[ClubId],
    participatingPlayerIds: Vector[PlayerId],
    adminIds: Vector[PlayerId],
    whitelistCount: Int,
    stages: Vector[TournamentStageSummaryView]
) derives CanEqual

object TournamentSummaryView:
  def fromDomain(tournament: Tournament): TournamentSummaryView =
    TournamentSummaryView(
      tournamentId = tournament.id,
      name = tournament.name,
      organizer = tournament.organizer,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      status = tournament.status,
      participatingClubIds = tournament.participatingClubs,
      participatingPlayerIds = tournament.participatingPlayers,
      adminIds = tournament.admins,
      whitelistCount = tournament.whitelist.size,
      stages = tournament.stages.sortBy(_.order).map(TournamentStageSummaryView.fromDomain)
    )

final case class TournamentWhitelistEntryView(
    participantKind: TournamentParticipantKind,
    playerId: Option[PlayerId],
    clubId: Option[ClubId]
) derives CanEqual

object TournamentWhitelistEntryView:
  def fromDomain(entry: TournamentWhitelistEntry): TournamentWhitelistEntryView =
    TournamentWhitelistEntryView(entry.participantKind, entry.playerId, entry.clubId)

final case class TournamentTableSeatView(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int,
    disconnected: Boolean,
    ready: Boolean,
    clubId: Option[ClubId]
) derives CanEqual

object TournamentTableSeatView:
  def fromDomain(seat: TableSeat): TournamentTableSeatView =
    TournamentTableSeatView(
      seat = seat.seat,
      playerId = seat.playerId,
      initialPoints = seat.initialPoints,
      disconnected = seat.disconnected,
      ready = seat.ready,
      clubId = seat.clubId
    )

final case class TournamentTableView(
    tableId: TableId,
    tableNo: Int,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TournamentTableSeatView],
    stageRoundNumber: Int,
    bracketMatchId: Option[String],
    bracketRoundNumber: Option[Int],
    status: TableStatus,
    startedAt: Option[Instant],
    scoringStartedAt: Option[Instant],
    endedAt: Option[Instant],
    paifuId: Option[PaifuId],
    matchRecordId: Option[MatchRecordId],
    appealTicketIds: Vector[AppealTicketId],
    resetCount: Int
) derives CanEqual

object TournamentTableView:
  def fromDomain(table: Table): TournamentTableView =
    TournamentTableView(
      tableId = table.id,
      tableNo = table.tableNo,
      tournamentId = table.tournamentId,
      stageId = table.stageId,
      seats = table.seats.map(TournamentTableSeatView.fromDomain),
      stageRoundNumber = table.stageRoundNumber,
      bracketMatchId = table.bracketMatchId,
      bracketRoundNumber = table.bracketRoundNumber,
      status = table.status,
      startedAt = table.startedAt,
      scoringStartedAt = table.scoringStartedAt,
      endedAt = table.endedAt,
      paifuId = table.paifuId,
      matchRecordId = table.matchRecordId,
      appealTicketIds = table.appealTicketIds,
      resetCount = table.resetCount
    )

final case class TournamentMatchRecordSeatResultView(
    playerId: PlayerId,
    seat: SeatWind,
    clubId: Option[ClubId],
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentMatchRecordSeatResultView:
  def fromDomain(result: MatchRecordSeatResult): TournamentMatchRecordSeatResultView =
    TournamentMatchRecordSeatResultView(
      playerId = result.playerId,
      seat = result.seat,
      clubId = result.clubId,
      finalPoints = result.finalPoints,
      placement = result.placement,
      scoreDelta = result.scoreDelta,
      uma = result.uma,
      oka = result.oka
    )

final case class TournamentMatchRecordView(
    recordId: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    stageRoundNumber: Int,
    generatedAt: Instant,
    seatResults: Vector[TournamentMatchRecordSeatResultView],
    paifuId: Option[PaifuId],
    finalizedBy: Option[PlayerId],
    sourceEvent: String,
    notes: Vector[String]
) derives CanEqual

object TournamentMatchRecordView:
  def fromDomain(record: MatchRecord): TournamentMatchRecordView =
    TournamentMatchRecordView(
      recordId = record.id,
      tableId = record.tableId,
      tournamentId = record.tournamentId,
      stageId = record.stageId,
      stageRoundNumber = record.stageRoundNumber,
      generatedAt = record.generatedAt,
      seatResults = record.seatResults.map(TournamentMatchRecordSeatResultView.fromDomain),
      paifuId = record.paifuId,
      finalizedBy = record.finalizedBy,
      sourceEvent = record.sourceEvent,
      notes = record.notes
    )

final case class TournamentPaifuFinalStandingView(
    playerId: PlayerId,
    seat: SeatWind,
    finalPoints: Int,
    placement: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentPaifuFinalStandingView:
  def fromDomain(standing: FinalStanding): TournamentPaifuFinalStandingView =
    TournamentPaifuFinalStandingView(
      playerId = standing.playerId,
      seat = standing.seat,
      finalPoints = standing.finalPoints,
      placement = standing.placement,
      uma = standing.uma,
      oka = standing.oka
    )

final case class TournamentPaifuSummaryView(
    paifuId: PaifuId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    recordedAt: Instant,
    source: String,
    matchRecordId: Option[MatchRecordId],
    totalHands: Int,
    playerIds: Vector[PlayerId],
    finalStandings: Vector[TournamentPaifuFinalStandingView]
) derives CanEqual

object TournamentPaifuSummaryView:
  def fromDomain(paifu: Paifu): TournamentPaifuSummaryView =
    TournamentPaifuSummaryView(
      paifuId = paifu.id,
      tableId = paifu.metadata.tableId,
      tournamentId = paifu.metadata.tournamentId,
      stageId = paifu.metadata.stageId,
      recordedAt = paifu.metadata.recordedAt,
      source = paifu.metadata.source,
      matchRecordId = paifu.metadata.matchRecordId,
      totalHands = paifu.totalHands,
      playerIds = paifu.playerIds,
      finalStandings = paifu.finalStandings.map(TournamentPaifuFinalStandingView.fromDomain)
    )

final case class TournamentSettlementAdjustmentView(
    playerId: PlayerId,
    label: String,
    amount: Long,
    note: Option[String]
) derives CanEqual

object TournamentSettlementAdjustmentView:
  def fromDomain(adjustment: TournamentSettlementAdjustment): TournamentSettlementAdjustmentView =
    TournamentSettlementAdjustmentView(adjustment.playerId, adjustment.label, adjustment.amount, adjustment.note)

final case class TournamentSettlementEntryView(
    playerId: PlayerId,
    rank: Int,
    awardAmount: Long,
    baseAwardAmount: Long,
    adjustmentAmount: Long,
    deductionAmount: Long,
    clubId: Option[ClubId],
    clubShareAmount: Long,
    playerRetainedAmount: Long,
    finalPoints: Int,
    champion: Boolean
) derives CanEqual

object TournamentSettlementEntryView:
  def fromDomain(entry: TournamentSettlementEntry): TournamentSettlementEntryView =
    TournamentSettlementEntryView(
      playerId = entry.playerId,
      rank = entry.rank,
      awardAmount = entry.awardAmount,
      baseAwardAmount = entry.baseAwardAmount,
      adjustmentAmount = entry.adjustmentAmount,
      deductionAmount = entry.deductionAmount,
      clubId = entry.clubId,
      clubShareAmount = entry.clubShareAmount,
      playerRetainedAmount = entry.playerRetainedAmount,
      finalPoints = entry.finalPoints,
      champion = entry.champion
    )

final case class TournamentSettlementView(
    settlementId: SettlementSnapshotId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    revision: Int,
    status: TournamentSettlementStatus,
    generatedAt: Instant,
    finalizedAt: Option[Instant],
    supersededAt: Option[Instant],
    supersedesSettlementId: Option[SettlementSnapshotId],
    championId: PlayerId,
    prizePool: Long,
    houseFeeAmount: Long,
    netPrizePool: Long,
    clubShareRatio: Double,
    adjustments: Vector[TournamentSettlementAdjustmentView],
    entries: Vector[TournamentSettlementEntryView],
    summary: String
) derives CanEqual

object TournamentSettlementView:
  def fromDomain(snapshot: TournamentSettlementSnapshot): TournamentSettlementView =
    TournamentSettlementView(
      settlementId = snapshot.id,
      tournamentId = snapshot.tournamentId,
      stageId = snapshot.stageId,
      revision = snapshot.revision,
      status = snapshot.status,
      generatedAt = snapshot.generatedAt,
      finalizedAt = snapshot.finalizedAt,
      supersededAt = snapshot.supersededAt,
      supersedesSettlementId = snapshot.supersedesSettlementId,
      championId = snapshot.championId,
      prizePool = snapshot.prizePool,
      houseFeeAmount = snapshot.houseFeeAmount,
      netPrizePool = snapshot.netPrizePool,
      clubShareRatio = snapshot.clubShareRatio,
      adjustments = snapshot.adjustments.map(TournamentSettlementAdjustmentView.fromDomain),
      entries = snapshot.entries.map(TournamentSettlementEntryView.fromDomain),
      summary = snapshot.summary
    )

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
) derives CanEqual

type TournamentStageDirectoryResponse = TournamentStageDirectoryEntry
type TournamentStageSummaryResponse = TournamentStageSummaryView
type TournamentSummaryResponse = TournamentSummaryView
type TournamentWhitelistEntryResponse = TournamentWhitelistEntryView
type TournamentTableResponse = TournamentTableView
type TournamentMatchRecordResponse = TournamentMatchRecordView
type TournamentPaifuSummaryResponse = TournamentPaifuSummaryView
type TournamentSettlementResponse = TournamentSettlementView
type TournamentParticipantClubResponse = TournamentParticipantClubView
type TournamentParticipantPlayerResponse = TournamentParticipantPlayerView
type TournamentWhitelistSummaryResponse = TournamentWhitelistSummaryView
type TournamentLineupSubmissionResponse = TournamentLineupSubmissionView
type TournamentOperationsStageResponse = TournamentOperationsStageView
type TournamentDetailResponse = TournamentDetailView
type TournamentMutationResponse = TournamentMutationView

object TournamentOperationResponses:
  export TournamentResponseCodecs.given
