package riichinexus.domain.model

import java.time.Instant

enum StageFormat derives CanEqual:
  case Swiss
  case Knockout
  case RoundRobin
  case Finals
  case Custom

enum StageStatus derives CanEqual:
  case Pending
  case Ready
  case Active
  case Completed
  case Archived

enum AdvancementRuleType derives CanEqual:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

final case class AdvancementRule(
    ruleType: AdvancementRuleType,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    templateKey: Option[String] = None,
    note: Option[String] = None
) derives CanEqual

object AdvancementRule:
  def defaultFor(format: StageFormat): AdvancementRule =
    format match
      case StageFormat.Swiss =>
        AdvancementRule(AdvancementRuleType.SwissCut, cutSize = Some(16))
      case StageFormat.Knockout =>
        AdvancementRule(AdvancementRuleType.KnockoutElimination, targetTableCount = Some(1))
      case StageFormat.RoundRobin =>
        AdvancementRule(AdvancementRuleType.ScoreThreshold, thresholdScore = Some(0))
      case StageFormat.Finals =>
        AdvancementRule(AdvancementRuleType.KnockoutElimination, targetTableCount = Some(1))
      case StageFormat.Custom =>
        AdvancementRule(AdvancementRuleType.Custom, note = Some("custom policy"))

final case class SwissRuleConfig(
    pairingMethod: String = "balanced-elo",
    carryOverPoints: Boolean = true,
    maxRounds: Option[Int] = None
) derives CanEqual:
  private val supportedPairingMethods = Set("balanced-elo", "snake")
  require(
    supportedPairingMethods.contains(pairingMethod.trim.toLowerCase),
    s"Unsupported swiss pairing method: $pairingMethod"
  )

final case class KnockoutRuleConfig(
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Boolean = false,
    seedingPolicy: String = "rating",
    repechageEnabled: Boolean = false
) derives CanEqual:
  private val supportedPolicies = Set("rating", "elo", "ranking", "standings")
  require(
    supportedPolicies.contains(seedingPolicy.trim.toLowerCase),
    s"Unsupported knockout seeding policy: $seedingPolicy"
  )

enum KnockoutLane derives CanEqual:
  case Championship
  case Bronze
  case Repechage

enum SeatWind derives CanEqual:
  case East
  case South
  case West
  case North

object SeatWind:
  val all: Vector[SeatWind] = Vector(East, South, West, North)

final case class StageLineupSeat(
    playerId: PlayerId,
    preferredWind: Option[SeatWind] = None,
    reserve: Boolean = false
) derives CanEqual

final case class StageLineupSubmission(
    id: LineupSubmissionId,
    clubId: ClubId,
    submittedBy: PlayerId,
    submittedAt: Instant,
    seats: Vector[StageLineupSeat],
    note: Option[String] = None
) derives CanEqual:
  require(seats.nonEmpty, "Lineup submission must contain at least one seat")
  require(
    seats.map(_.playerId).distinct.size == seats.size,
    "Lineup submission cannot contain duplicate players"
  )
  require(
    seats.exists(seat => !seat.reserve),
    "Lineup submission must contain at least one active player"
  )

  def activePlayerIds: Vector[PlayerId] =
    seats.filterNot(_.reserve).map(_.playerId)

final case class StageTablePlan(
    roundNumber: Int,
    tableNo: Int,
    seats: Vector[TableSeat]
) derives CanEqual:
  require(roundNumber >= 1, "Stage table plan round number must be positive")
  require(seats.size == 4, "Stage table plan must contain four seats")

final case class TournamentStage(
    id: TournamentStageId,
    name: String,
    format: StageFormat,
    order: Int,
    roundCount: Int,
    currentRound: Int = 1,
    status: StageStatus = StageStatus.Pending,
    advancementRule: AdvancementRule = AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured")),
    swissRule: Option[SwissRuleConfig] = None,
    knockoutRule: Option[KnockoutRuleConfig] = None,
    schedulingPoolSize: Int = 4,
    lineupSubmissions: Vector[StageLineupSubmission] = Vector.empty,
    pendingTablePlans: Vector[StageTablePlan] = Vector.empty,
    scheduledTableIds: Vector[TableId] = Vector.empty
) derives CanEqual:
  require(order >= 1, "Stage order must be positive")
  require(roundCount >= 1, "Stage round count must be positive")
  require(currentRound >= 1 && currentRound <= roundCount, "Current round must be within stage bounds")
  require(schedulingPoolSize >= 1, "Scheduling pool size must be positive")

  def withRules(
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig],
      schedulingPoolSize: Int
  ): TournamentStage =
    require(schedulingPoolSize >= 1, "Scheduling pool size must be positive")
    copy(
      advancementRule = advancementRule,
      swissRule = swissRule,
      knockoutRule = knockoutRule,
      schedulingPoolSize = schedulingPoolSize
    )

  def submitLineup(submission: StageLineupSubmission): TournamentStage =
    require(
      status != StageStatus.Completed && status != StageStatus.Archived,
      "Cannot submit lineups to a completed stage"
    )
    copy(
      status = StageStatus.Ready,
      lineupSubmissions =
        lineupSubmissions.filterNot(_.clubId == submission.clubId) :+ submission
    )

  def queueRoundPlans(
      roundNumber: Int,
      plans: Vector[StageTablePlan]
  ): TournamentStage =
    require(roundNumber >= 1 && roundNumber <= roundCount, "Round number is out of bounds")
    require(plans.forall(_.roundNumber == roundNumber), "Queued plans must share the same round number")
    copy(
      currentRound = roundNumber,
      status = StageStatus.Active,
      pendingTablePlans = plans
    )

  def consumePendingPlans(
      materializedPlans: Vector[StageTablePlan],
      tableIds: Vector[TableId]
  ): TournamentStage =
    require(
      materializedPlans.size == tableIds.size,
      "Materialized plans and table ids must have the same size"
    )
    val consumedKeys = materializedPlans.map(plan => plan.roundNumber -> plan.tableNo).toSet
    copy(
      status = StageStatus.Active,
      pendingTablePlans =
        pendingTablePlans.filterNot(plan => consumedKeys.contains(plan.roundNumber -> plan.tableNo)),
      scheduledTableIds = (scheduledTableIds ++ tableIds).distinct
    )

  def advanceRound(nextRound: Int): TournamentStage =
    require(nextRound >= 1 && nextRound <= roundCount, "Next round is out of bounds")
    copy(currentRound = nextRound)

  def registerScheduledTables(tableIds: Vector[TableId]): TournamentStage =
    require(tableIds.nonEmpty, "Scheduled tables cannot be empty")
    copy(
      status = StageStatus.Active,
      scheduledTableIds = (scheduledTableIds ++ tableIds).distinct
    )

  def complete: TournamentStage =
    copy(status = StageStatus.Completed)

final case class StageStandingEntry(
    playerId: PlayerId,
    matchesPlayed: Int,
    placementPoints: Int,
    totalScoreDelta: Int,
    totalFinalPoints: Int,
    averagePlacement: Double,
    qualified: Boolean = false,
    seed: Option[Int] = None
) derives CanEqual

final case class StageRankingSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    entries: Vector[StageStandingEntry],
    archivedTableCount: Int,
    scheduledTableCount: Int
) derives CanEqual:
  def qualifiedPlayerIds: Vector[PlayerId] =
    entries.filter(_.qualified).map(_.playerId)

final case class StageAdvancementSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    rule: AdvancementRule,
    standings: Vector[StageStandingEntry],
    qualifiedPlayerIds: Vector[PlayerId],
    reservePlayerIds: Vector[PlayerId] = Vector.empty,
    summary: String
) derives CanEqual

final case class KnockoutBracketSlot(
    seed: Int,
    playerId: Option[PlayerId],
    bye: Boolean = false,
    sourceMatchId: Option[String] = None,
    sourcePlacement: Option[Int] = None
) derives CanEqual

final case class KnockoutBracketResult(
    playerId: PlayerId,
    placement: Int,
    finalPoints: Int,
    advanced: Boolean
) derives CanEqual

final case class KnockoutBracketMatch(
    id: String,
    roundNumber: Int,
    position: Int,
    lane: KnockoutLane = KnockoutLane.Championship,
    slots: Vector[KnockoutBracketSlot],
    sourceMatchIds: Vector[String] = Vector.empty,
    advancementCount: Int,
    nextMatchId: Option[String] = None,
    tableId: Option[TableId] = None,
    unlocked: Boolean = false,
    completed: Boolean = false,
    results: Vector[KnockoutBracketResult] = Vector.empty
) derives CanEqual:
  require(slots.size == 4, "Riichi knockout matches must contain exactly four slots")
  require(advancementCount >= 0 && advancementCount <= 4, "Advancement count must be between 0 and 4")

final case class KnockoutBracketRound(
    roundNumber: Int,
    label: String,
    matches: Vector[KnockoutBracketMatch]
) derives CanEqual

final case class KnockoutBracketSnapshot(
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    bracketSize: Int,
    qualifiedPlayerIds: Vector[PlayerId],
    rounds: Vector[KnockoutBracketRound],
    summary: String
) derives CanEqual
