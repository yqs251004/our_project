package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.{
  AdvancementRule as DomainAdvancementRule,
  KnockoutBracketMatch as DomainKnockoutBracketMatch,
  KnockoutBracketResult as DomainKnockoutBracketResult,
  KnockoutBracketRound as DomainKnockoutBracketRound,
  KnockoutBracketSlot as DomainKnockoutBracketSlot,
  KnockoutBracketSnapshot as DomainKnockoutBracketSnapshot,
  KnockoutRuleConfig as DomainKnockoutRuleConfig,
  RankSnapshot as DomainRankSnapshot,
  StageAdvancementSnapshot as DomainStageAdvancementSnapshot,
  StageRankingSnapshot as DomainStageRankingSnapshot,
  StageStandingEntry as DomainStageStandingEntry,
  SwissRuleConfig as DomainSwissRuleConfig,
  Table as DomainTable,
  TableSeat as DomainTableSeat
}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

type SeatWind = String
type TournamentFormat = String
type RankPlatform = String

final case class RankSnapshotView(
    platform: RankPlatform,
    tier: String,
    stars: Option[Int]
) derives ReadWriter

object RankSnapshotView:
  def fromDomain(rank: DomainRankSnapshot): RankSnapshotView =
    RankSnapshotView(
      platform = rank.platform.toString,
      tier = rank.tier,
      stars = rank.stars
    )

final case class AdvancementRuleView(
    ruleType: String,
    cutSize: Option[Int],
    thresholdScore: Option[Int],
    targetTableCount: Option[Int],
    templateKey: Option[String],
    note: Option[String]
) derives ReadWriter

object AdvancementRuleView:
  def fromDomain(rule: DomainAdvancementRule): AdvancementRuleView =
    AdvancementRuleView(
      ruleType = rule.ruleType.toString,
      cutSize = rule.cutSize,
      thresholdScore = rule.thresholdScore,
      targetTableCount = rule.targetTableCount,
      templateKey = rule.templateKey,
      note = rule.note
    )

final case class SwissRuleConfigView(
    pairingMethod: String,
    carryOverPoints: Boolean,
    maxRounds: Option[Int]
) derives ReadWriter

object SwissRuleConfigView:
  def fromDomain(config: DomainSwissRuleConfig): SwissRuleConfigView =
    SwissRuleConfigView(
      pairingMethod = config.pairingMethod,
      carryOverPoints = config.carryOverPoints,
      maxRounds = config.maxRounds
    )

final case class KnockoutRuleConfigView(
    bracketSize: Option[Int],
    thirdPlaceMatch: Boolean,
    seedingPolicy: String,
    repechageEnabled: Boolean
) derives ReadWriter

object KnockoutRuleConfigView:
  def fromDomain(config: DomainKnockoutRuleConfig): KnockoutRuleConfigView =
    KnockoutRuleConfigView(
      bracketSize = config.bracketSize,
      thirdPlaceMatch = config.thirdPlaceMatch,
      seedingPolicy = config.seedingPolicy,
      repechageEnabled = config.repechageEnabled
    )

final case class TableSeat(
    seat: SeatWind,
    playerId: String,
    initialPoints: Int,
    disconnected: Boolean,
    ready: Boolean,
    clubId: Option[String]
) derives ReadWriter

object TableSeat:
  def fromDomain(seat: DomainTableSeat): TableSeat =
    TableSeat(
      seat = seat.seat.toString,
      playerId = seat.playerId.value,
      initialPoints = seat.initialPoints,
      disconnected = seat.disconnected,
      ready = seat.ready,
      clubId = seat.clubId.map(_.value)
    )

final case class Table(
    id: String,
    tableNo: Int,
    tournamentId: String,
    stageId: String,
    seats: Vector[TableSeat],
    stageRoundNumber: Int,
    bracketMatchId: Option[String],
    bracketRoundNumber: Option[Int],
    feederMatchIds: Vector[String],
    status: String,
    startedAt: Option[String],
    scoringStartedAt: Option[String],
    endedAt: Option[String],
    paifuId: Option[String],
    matchRecordId: Option[String],
    appealTicketIds: Vector[String],
    resetCount: Int,
    operatorNotes: Vector[String],
    version: Int
) derives ReadWriter

object Table:
  def fromDomain(table: DomainTable): Table =
    Table(
      id = table.id.value,
      tableNo = table.tableNo,
      tournamentId = table.tournamentId.value,
      stageId = table.stageId.value,
      seats = table.seats.map(TableSeat.fromDomain),
      stageRoundNumber = table.stageRoundNumber,
      bracketMatchId = table.bracketMatchId,
      bracketRoundNumber = table.bracketRoundNumber,
      feederMatchIds = table.feederMatchIds,
      status = table.status.toString,
      startedAt = table.startedAt.map(_.toString),
      scoringStartedAt = table.scoringStartedAt.map(_.toString),
      endedAt = table.endedAt.map(_.toString),
      paifuId = table.paifuId.map(_.value),
      matchRecordId = table.matchRecordId.map(_.value),
      appealTicketIds = table.appealTicketIds.map(_.value),
      resetCount = table.resetCount,
      operatorNotes = table.operatorNotes,
      version = table.version
    )

final case class StageStandingEntry(
    playerId: String,
    matchesPlayed: Int,
    placementPoints: Int,
    totalScoreDelta: Int,
    totalFinalPoints: Int,
    averagePlacement: Double,
    qualified: Boolean,
    seed: Option[Int]
) derives ReadWriter

object StageStandingEntry:
  def fromDomain(entry: DomainStageStandingEntry): StageStandingEntry =
    StageStandingEntry(
      playerId = entry.playerId.value,
      matchesPlayed = entry.matchesPlayed,
      placementPoints = entry.placementPoints,
      totalScoreDelta = entry.totalScoreDelta,
      totalFinalPoints = entry.totalFinalPoints,
      averagePlacement = entry.averagePlacement,
      qualified = entry.qualified,
      seed = entry.seed
    )

final case class StageRankingSnapshot(
    tournamentId: String,
    stageId: String,
    generatedAt: String,
    entries: Vector[StageStandingEntry],
    archivedTableCount: Int,
    scheduledTableCount: Int
) derives ReadWriter

object StageRankingSnapshot:
  def fromDomain(snapshot: DomainStageRankingSnapshot): StageRankingSnapshot =
    StageRankingSnapshot(
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      generatedAt = snapshot.generatedAt.toString,
      entries = snapshot.entries.map(StageStandingEntry.fromDomain),
      archivedTableCount = snapshot.archivedTableCount,
      scheduledTableCount = snapshot.scheduledTableCount
    )

final case class StageAdvancementSnapshot(
    tournamentId: String,
    stageId: String,
    generatedAt: String,
    rule: String,
    standings: Vector[StageStandingEntry],
    qualifiedPlayerIds: Vector[String],
    reservePlayerIds: Vector[String],
    summary: String
) derives ReadWriter

object StageAdvancementSnapshot:
  def fromDomain(snapshot: DomainStageAdvancementSnapshot): StageAdvancementSnapshot =
    StageAdvancementSnapshot(
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      generatedAt = snapshot.generatedAt.toString,
      rule = snapshot.rule.ruleType.toString,
      standings = snapshot.standings.map(StageStandingEntry.fromDomain),
      qualifiedPlayerIds = snapshot.qualifiedPlayerIds.map(_.value),
      reservePlayerIds = snapshot.reservePlayerIds.map(_.value),
      summary = snapshot.summary
    )

final case class KnockoutBracketSlot(
    seed: Int,
    playerId: Option[String],
    bye: Boolean,
    sourceMatchId: Option[String],
    sourcePlacement: Option[Int]
) derives ReadWriter

object KnockoutBracketSlot:
  def fromDomain(slot: DomainKnockoutBracketSlot): KnockoutBracketSlot =
    KnockoutBracketSlot(
      seed = slot.seed,
      playerId = slot.playerId.map(_.value),
      bye = slot.bye,
      sourceMatchId = slot.sourceMatchId,
      sourcePlacement = slot.sourcePlacement
    )

final case class KnockoutBracketResult(
    playerId: String,
    placement: Int,
    finalPoints: Int,
    advanced: Boolean
) derives ReadWriter

object KnockoutBracketResult:
  def fromDomain(result: DomainKnockoutBracketResult): KnockoutBracketResult =
    KnockoutBracketResult(
      playerId = result.playerId.value,
      placement = result.placement,
      finalPoints = result.finalPoints,
      advanced = result.advanced
    )

final case class KnockoutBracketMatch(
    id: String,
    roundNumber: Int,
    position: Int,
    lane: String,
    slots: Vector[KnockoutBracketSlot],
    sourceMatchIds: Vector[String],
    advancementCount: Int,
    nextMatchId: Option[String],
    tableId: Option[String],
    unlocked: Boolean,
    completed: Boolean,
    results: Vector[KnockoutBracketResult]
) derives ReadWriter

object KnockoutBracketMatch:
  def fromDomain(matchView: DomainKnockoutBracketMatch): KnockoutBracketMatch =
    KnockoutBracketMatch(
      id = matchView.id,
      roundNumber = matchView.roundNumber,
      position = matchView.position,
      lane = matchView.lane.toString,
      slots = matchView.slots.map(KnockoutBracketSlot.fromDomain),
      sourceMatchIds = matchView.sourceMatchIds,
      advancementCount = matchView.advancementCount,
      nextMatchId = matchView.nextMatchId,
      tableId = matchView.tableId.map(_.value),
      unlocked = matchView.unlocked,
      completed = matchView.completed,
      results = matchView.results.map(KnockoutBracketResult.fromDomain)
    )

final case class KnockoutBracketRound(
    roundNumber: Int,
    label: String,
    matches: Vector[KnockoutBracketMatch]
) derives ReadWriter

object KnockoutBracketRound:
  def fromDomain(round: DomainKnockoutBracketRound): KnockoutBracketRound =
    KnockoutBracketRound(
      roundNumber = round.roundNumber,
      label = round.label,
      matches = round.matches.map(KnockoutBracketMatch.fromDomain)
    )

final case class KnockoutBracketSnapshot(
    tournamentId: String,
    stageId: String,
    generatedAt: String,
    bracketSize: Int,
    qualifiedPlayerIds: Vector[String],
    rounds: Vector[KnockoutBracketRound],
    summary: String
) derives ReadWriter

object KnockoutBracketSnapshot:
  def fromDomain(snapshot: DomainKnockoutBracketSnapshot): KnockoutBracketSnapshot =
    KnockoutBracketSnapshot(
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      generatedAt = snapshot.generatedAt.toString,
      bracketSize = snapshot.bracketSize,
      qualifiedPlayerIds = snapshot.qualifiedPlayerIds.map(_.value),
      rounds = snapshot.rounds.map(KnockoutBracketRound.fromDomain),
      summary = snapshot.summary
    )
