package riichinexus.domain.model

import java.time.Instant
import java.util.NoSuchElementException

enum TournamentStatus derives CanEqual:
  case Draft
  case RegistrationOpen
  case Scheduled
  case InProgress
  case Completed
  case Cancelled
  case Archived

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

final case class TournamentSettlementEntry(
    playerId: PlayerId,
    rank: Int,
    awardAmount: Long,
    finalPoints: Int,
    champion: Boolean = false
) derives CanEqual

final case class TournamentSettlementSnapshot(
    id: SettlementSnapshotId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    generatedAt: Instant,
    championId: PlayerId,
    prizePool: Long,
    entries: Vector[TournamentSettlementEntry],
    summary: String
) derives CanEqual

enum TournamentParticipantKind derives CanEqual:
  case Club
  case Player

final case class TournamentWhitelistEntry(
    participantKind: TournamentParticipantKind,
    clubId: Option[ClubId] = None,
    playerId: Option[PlayerId] = None
) derives CanEqual:
  require(
    participantKind match
      case TournamentParticipantKind.Club => clubId.nonEmpty && playerId.isEmpty
      case TournamentParticipantKind.Player => playerId.nonEmpty && clubId.isEmpty,
    s"Invalid whitelist entry for $participantKind"
  )

final case class Tournament(
    id: TournamentId,
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[ClubId] = Vector.empty,
    participatingPlayers: Vector[PlayerId] = Vector.empty,
    admins: Vector[PlayerId] = Vector.empty,
    whitelist: Vector[TournamentWhitelistEntry] = Vector.empty,
    stages: Vector[TournamentStage] = Vector.empty,
    status: TournamentStatus = TournamentStatus.Draft
) derives CanEqual:
  require(startsAt.isBefore(endsAt), "Tournament start time must be earlier than end time")
  require(stages.map(_.id).distinct.size == stages.size, "Tournament stages must have unique ids")
  require(
    stages.map(_.order).distinct.size == stages.size,
    "Tournament stages must have unique ordering"
  )

  def registerClub(clubId: ClubId): Tournament =
    copy(
      participatingClubs = (participatingClubs :+ clubId).distinct,
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Club, clubId = Some(clubId))).distinct
    )

  def registerPlayer(playerId: PlayerId): Tournament =
    copy(
      participatingPlayers = (participatingPlayers :+ playerId).distinct,
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Player, playerId = Some(playerId))).distinct
    )

  def whitelistClub(clubId: ClubId): Tournament =
    copy(
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Club, clubId = Some(clubId))).distinct
    )

  def whitelistPlayer(playerId: PlayerId): Tournament =
    copy(
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Player, playerId = Some(playerId))).distinct
    )

  def assignAdmin(playerId: PlayerId): Tournament =
    copy(admins = (admins :+ playerId).distinct)

  def addStage(stage: TournamentStage): Tournament =
    val updatedStages = (stages.filterNot(_.id == stage.id) :+ stage).sortBy(_.order)
    require(
      updatedStages.map(_.order).distinct.size == updatedStages.size,
      "Tournament stages must have unique ordering"
    )
    copy(stages = updatedStages)

  def updateStage(
      stageId: TournamentStageId,
      update: TournamentStage => TournamentStage
  ): Tournament =
    val updatedStages = stages.map(stage => if stage.id == stageId then update(stage) else stage)
    require(
      updatedStages.map(_.order).distinct.size == updatedStages.size,
      "Tournament stages must have unique ordering"
    )
    copy(stages = updatedStages)

  def publish: Tournament =
    require(status == TournamentStatus.Draft, "Only draft tournaments can be published")
    copy(status = TournamentStatus.RegistrationOpen)

  def markScheduled: Tournament =
    require(
      status == TournamentStatus.RegistrationOpen || status == TournamentStatus.InProgress,
      "Only published or active tournaments can be marked as scheduled"
    )
    copy(status = TournamentStatus.Scheduled)

  def start: Tournament =
    require(
      status == TournamentStatus.RegistrationOpen || status == TournamentStatus.Scheduled,
      "Only published or scheduled tournaments can start"
    )
    copy(status = TournamentStatus.InProgress)

  def complete: Tournament =
    copy(
      status = TournamentStatus.Completed,
      stages = stages.map(_.complete)
    )

  def cancel: Tournament =
    require(status != TournamentStatus.Archived, "Archived tournaments cannot be cancelled")
    copy(status = TournamentStatus.Cancelled)

  def archive: Tournament =
    require(status == TournamentStatus.Completed, "Only completed tournaments can be archived")
    copy(status = TournamentStatus.Archived)

  def activateStage(stageId: TournamentStageId): Tournament =
    require(stages.exists(_.id == stageId), s"Stage ${stageId.value} was not found")
    copy(
      status = TournamentStatus.InProgress,
      stages = stages.map { stage =>
        if stage.id == stageId then stage.copy(status = StageStatus.Active)
        else if stage.status == StageStatus.Active then stage.copy(status = StageStatus.Ready)
        else stage
      }
    )

final case class TableSeat(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int = 25000,
    disconnected: Boolean = false,
    ready: Boolean = false,
    clubId: Option[ClubId] = None
) derives CanEqual:
  require(initialPoints > 0, "Seat initial points must be positive")

  def markReady: TableSeat =
    require(!disconnected, "Disconnected seats cannot be marked ready")
    copy(ready = true)

  def markNotReady: TableSeat =
    copy(ready = false)

  def markDisconnected: TableSeat =
    copy(disconnected = true, ready = false)

  def markConnected: TableSeat =
    copy(disconnected = false)

enum TableStatus derives CanEqual:
  case WaitingPreparation
  case InProgress
  case Scoring
  case Archived
  case AppealInProgress

object TableStatus:
  val Pending: TableStatus = WaitingPreparation
  val Finished: TableStatus = Archived

enum AppealTableResolution derives CanEqual:
  case RestorePriorState
  case ArchiveTable
  case ResumeScoring
  case ResumePlay
  case ForceReset

final case class Table(
    id: TableId,
    tableNo: Int,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    stageRoundNumber: Int = 1,
    bracketMatchId: Option[String] = None,
    bracketRoundNumber: Option[Int] = None,
    feederMatchIds: Vector[String] = Vector.empty,
    status: TableStatus = TableStatus.WaitingPreparation,
    startedAt: Option[Instant] = None,
    scoringStartedAt: Option[Instant] = None,
    endedAt: Option[Instant] = None,
    paifuId: Option[PaifuId] = None,
    matchRecordId: Option[MatchRecordId] = None,
    appealTicketIds: Vector[AppealTicketId] = Vector.empty,
    resetCount: Int = 0,
    operatorNotes: Vector[String] = Vector.empty
) derives CanEqual:
  require(seats.size == 4, "A riichi table must have exactly four seats")
  require(seats.map(_.seat).distinct.size == 4, "Seats must be unique")
  require(stageRoundNumber >= 1, "Stage round number must be positive")

  def seatFor(wind: SeatWind): TableSeat =
    seats.find(_.seat == wind).getOrElse(
      throw NoSuchElementException(s"Seat $wind was not found on table ${id.value}")
    )

  def allSeatsReady: Boolean =
    seats.forall(_.ready)

  def hasDisconnectedSeats: Boolean =
    seats.exists(_.disconnected)

  def updateSeatState(
      targetSeat: SeatWind,
      ready: Option[Boolean] = None,
      disconnected: Option[Boolean] = None,
      note: Option[String] = None
  ): Table =
    require(status != TableStatus.Archived, "Archived tables cannot update seat state")
    if ready.isDefined then
      require(
        status == TableStatus.WaitingPreparation,
        "Seat readiness can only be updated before a table starts"
      )

    val updatedSeats = seats.map { seat =>
      if seat.seat != targetSeat then seat
      else
        val withConnection = disconnected match
          case Some(true)  => seat.markDisconnected
          case Some(false) => seat.markConnected
          case None        => seat

        ready match
          case Some(true)  => withConnection.markReady
          case Some(false) => withConnection.markNotReady
          case None        => withConnection
    }

    copy(
      seats = updatedSeats,
      operatorNotes = operatorNotes ++ note.toVector
    )

  def bindKnockoutMatch(
      matchId: String,
      roundNumber: Int,
      feeders: Vector[String] = Vector.empty
  ): Table =
    copy(
      bracketMatchId = Some(matchId),
      bracketRoundNumber = Some(roundNumber),
      feederMatchIds = feeders.distinct
    )

  def start(at: Instant): Table =
    require(
      status == TableStatus.WaitingPreparation,
      "Only waiting tables can be started"
    )
    val preparedSeats =
      if seats.forall(seat => !seat.ready && !seat.disconnected) then seats.map(_.markReady)
      else seats

    require(preparedSeats.forall(_.ready), "All seats must be ready before a table starts")
    require(!preparedSeats.exists(_.disconnected), "Disconnected seats must reconnect before a table starts")
    copy(status = TableStatus.InProgress, seats = preparedSeats, startedAt = Some(at))

  def enterScoring(at: Instant): Table =
    require(status == TableStatus.InProgress, "Only running tables can enter scoring")
    copy(status = TableStatus.Scoring, scoringStartedAt = Some(at))

  def archive(
      recordId: MatchRecordId,
      paifuId: PaifuId,
      at: Instant,
      note: Option[String] = None
  ): Table =
    require(status == TableStatus.Scoring, "Only scoring tables can be archived")
    copy(
      status = TableStatus.Archived,
      scoringStartedAt = Some(scoringStartedAt.getOrElse(at)),
      endedAt = Some(at),
      paifuId = Some(paifuId),
      matchRecordId = Some(recordId),
      operatorNotes = operatorNotes ++ note.toVector
    )

  def flagAppeal(ticketId: AppealTicketId, note: Option[String] = None): Table =
    require(status != TableStatus.Archived, "Archived tables cannot enter appeal flow")
    copy(
      status = TableStatus.AppealInProgress,
      appealTicketIds = (appealTicketIds :+ ticketId).distinct,
      operatorNotes = operatorNotes ++ note.toVector
    )

  def resolveAppeal(
      resolution: AppealTableResolution = AppealTableResolution.RestorePriorState,
      note: Option[String] = None
  ): Table =
    require(status == TableStatus.AppealInProgress, "Only appealed tables can resolve appeals")
    resolution match
      case AppealTableResolution.ForceReset =>
        forceReset(note.getOrElse("appeal adjudication requested a table reset"), Instant.now())
      case _ =>
        copy(
          status =
            resolution match
              case AppealTableResolution.RestorePriorState =>
                if endedAt.nonEmpty || matchRecordId.nonEmpty || paifuId.nonEmpty then TableStatus.Archived
                else if scoringStartedAt.nonEmpty then TableStatus.Scoring
                else if startedAt.nonEmpty then TableStatus.InProgress
                else TableStatus.WaitingPreparation
              case AppealTableResolution.ArchiveTable => TableStatus.Archived
              case AppealTableResolution.ResumeScoring => TableStatus.Scoring
              case AppealTableResolution.ResumePlay    => TableStatus.InProgress
              case AppealTableResolution.ForceReset    => TableStatus.WaitingPreparation
          ,
          operatorNotes = operatorNotes ++ note.toVector
        )

  def forceReset(note: String, at: Instant): Table =
    copy(
      status = TableStatus.WaitingPreparation,
      seats = seats.map(_.copy(disconnected = false, ready = false)),
      startedAt = None,
      scoringStartedAt = None,
      endedAt = None,
      paifuId = None,
      matchRecordId = None,
      resetCount = resetCount + 1,
      operatorNotes = operatorNotes :+ s"${at.toString}: $note"
    )

final case class MatchRecordSeatResult(
    playerId: PlayerId,
    seat: SeatWind,
    clubId: Option[ClubId] = None,
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
) derives CanEqual:
  require(placement >= 1 && placement <= 4, "Placement must be between 1 and 4")

final case class MatchRecord(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    stageRoundNumber: Int,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResult],
    paifuId: Option[PaifuId] = None,
    finalizedBy: Option[PlayerId] = None,
    sourceEvent: String = "table-state-machine",
    notes: Vector[String] = Vector.empty
) derives CanEqual:
  require(seatResults.size == 4, "Match record must contain four seat results")
  require(seatResults.map(_.playerId).distinct.size == 4, "Match record players must be unique")
  require(seatResults.map(_.seat).distinct.size == 4, "Match record seats must be unique")
  require(seatResults.map(_.placement).distinct.size == 4, "Match record placements must be unique")
  require(stageRoundNumber >= 1, "Match record stage round number must be positive")

  def playerIds: Vector[PlayerId] =
    seatResults.map(_.playerId)

object MatchRecord:
  def fromTableAndPaifu(
      table: Table,
      paifu: Paifu,
      generatedAt: Instant,
      finalizedBy: Option[PlayerId] = None
  ): MatchRecord =
    val seatMap = table.seats.map(seat => seat.playerId -> seat).toMap
    require(
      paifu.finalStandings.map(_.playerId).toSet == seatMap.keySet,
      "Paifu final standings must match scheduled table players"
    )

    val settlementNotes = paifu.rounds.zipWithIndex.flatMap { (round, index) =>
      round.result.settlement.map { settlement =>
        val noteSuffix =
          if settlement.notes.isEmpty then ""
          else s" notes=${settlement.notes.mkString("|")}"
        s"round-${index + 1}:${round.descriptor.roundWind}-${round.descriptor.handNumber} settlement riichi=${settlement.riichiSticksDelta} honba=${settlement.honbaPayment}$noteSuffix"
      }
    }

    MatchRecord(
      id = IdGenerator.matchRecordId(),
      tableId = table.id,
      tournamentId = table.tournamentId,
      stageId = table.stageId,
      stageRoundNumber = table.stageRoundNumber,
      generatedAt = generatedAt,
      seatResults = paifu.finalStandings.map { standing =>
        val scheduledSeat = seatMap(standing.playerId)
        MatchRecordSeatResult(
          playerId = standing.playerId,
          seat = standing.seat,
          clubId = scheduledSeat.clubId,
          finalPoints = standing.finalPoints,
          placement = standing.placement,
          scoreDelta = standing.finalPoints - scheduledSeat.initialPoints,
          uma = standing.uma,
          oka = standing.oka
        )
      },
      paifuId = Some(paifu.id),
      finalizedBy = finalizedBy,
      notes = settlementNotes
    )

final case class AppealAttachment(
    name: String,
    uri: String,
    contentType: Option[String] = None
) derives CanEqual

final case class AppealDecisionLog(
    operatorId: PlayerId,
    decision: String,
    decidedAt: Instant,
    note: Option[String] = None
) derives CanEqual

enum AppealStatus derives CanEqual:
  case Open
  case UnderReview
  case Resolved
  case Rejected
  case Escalated

enum AppealDecisionType derives CanEqual:
  case Resolve
  case Reject
  case Escalate

final case class AppealTicket(
    id: AppealTicketId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    openedBy: PlayerId,
    description: String,
    attachments: Vector[AppealAttachment] = Vector.empty,
    status: AppealStatus = AppealStatus.Open,
    logs: Vector[AppealDecisionLog] = Vector.empty,
    createdAt: Instant,
    updatedAt: Instant,
    resolution: Option[String] = None
) derives CanEqual:
  def markUnderReview(operatorId: PlayerId, at: Instant, note: Option[String] = None): AppealTicket =
    require(
      status == AppealStatus.Open || status == AppealStatus.Escalated,
      "Only open or escalated appeals can enter review"
    )
    copy(
      status = AppealStatus.UnderReview,
      logs = logs :+ AppealDecisionLog(operatorId, "under-review", at, note),
      updatedAt = at
    )

  def resolve(
      operatorId: PlayerId,
      verdict: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(verdict.trim.nonEmpty, "Appeal verdict cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview || status == AppealStatus.Escalated,
      "Only active appeals can be resolved"
    )
    copy(
      status = AppealStatus.Resolved,
      logs = logs :+ AppealDecisionLog(operatorId, verdict, at, note),
      updatedAt = at,
      resolution = Some(verdict)
    )

  def reject(
      operatorId: PlayerId,
      verdict: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(verdict.trim.nonEmpty, "Appeal rejection reason cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview || status == AppealStatus.Escalated,
      "Only active appeals can be rejected"
    )
    copy(
      status = AppealStatus.Rejected,
      logs = logs :+ AppealDecisionLog(operatorId, s"rejected:$verdict", at, note),
      updatedAt = at,
      resolution = Some(verdict)
    )

  def escalate(
      operatorId: PlayerId,
      reason: String,
      at: Instant,
      note: Option[String] = None
  ): AppealTicket =
    require(reason.trim.nonEmpty, "Appeal escalation reason cannot be empty")
    require(
      status == AppealStatus.Open || status == AppealStatus.UnderReview,
      "Only open or under-review appeals can be escalated"
    )
    copy(
      status = AppealStatus.Escalated,
      logs = logs :+ AppealDecisionLog(operatorId, s"escalated:$reason", at, note),
      updatedAt = at,
      resolution = Some(reason)
    )
