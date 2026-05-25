package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*

final case class TournamentStageDirectoryEntry(
    stageId: String,
    name: String,
    format: String,
    order: Int,
    status: String,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int
) derives CanEqual

object TournamentStageDirectoryEntry:
  def apply(
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
  ): TournamentStageDirectoryEntry =
    TournamentStageDirectoryEntry(
      stageId = stageId.value,
      name = name,
      format = format.toString,
      order = order,
      status = status.toString,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount
    )

final case class TournamentParticipantClubView(
    clubId: String,
    memberCount: Int
) derives CanEqual

object TournamentParticipantClubView:
  def apply(clubId: ClubId, memberCount: Int): TournamentParticipantClubView =
    TournamentParticipantClubView(clubId.value, memberCount)

final case class TournamentParticipantPlayerView(
    playerId: String,
    nickname: String,
    status: String,
    elo: Int,
    currentRank: RankSnapshotView,
    clubIds: Vector[String]
) derives CanEqual

object TournamentParticipantPlayerView:
  def apply(
      playerId: PlayerId,
      nickname: String,
      status: PlayerStatus,
      elo: Int,
      currentRank: RankSnapshot,
      clubIds: Vector[ClubId]
  ): TournamentParticipantPlayerView =
    TournamentParticipantPlayerView(
      playerId = playerId.value,
      nickname = nickname,
      status = status.toString,
      elo = elo,
      currentRank = RankSnapshotView.fromDomain(currentRank),
      clubIds = clubIds.map(_.value)
    )

final case class TournamentWhitelistSummaryView(
    totalEntries: Int,
    clubCount: Int,
    playerCount: Int,
    clubIds: Vector[String],
    playerIds: Vector[String]
) derives CanEqual

final case class TournamentLineupSubmissionView(
    submissionId: String,
    clubId: String,
    submittedBy: String,
    submittedAt: String,
    activePlayerIds: Vector[String],
    reservePlayerIds: Vector[String],
    note: Option[String]
) derives CanEqual

object TournamentLineupSubmissionView:
  def apply(
      submissionId: LineupSubmissionId,
      clubId: ClubId,
      submittedBy: PlayerId,
      submittedAt: Instant,
      activePlayerIds: Vector[PlayerId],
      reservePlayerIds: Vector[PlayerId],
      note: Option[String]
  ): TournamentLineupSubmissionView =
    TournamentLineupSubmissionView(
      submissionId = submissionId.value,
      clubId = clubId.value,
      submittedBy = submittedBy.value,
      submittedAt = submittedAt.toString,
      activePlayerIds = activePlayerIds.map(_.value),
      reservePlayerIds = reservePlayerIds.map(_.value),
      note = note
    )

final case class TournamentOperationsStageView(
    stageId: String,
    name: String,
    format: String,
    order: Int,
    status: String,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int,
    advancementRule: AdvancementRuleView = AdvancementRuleView.fromDomain(AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured"))),
    swissRule: Option[SwissRuleConfigView] = None,
    knockoutRule: Option[KnockoutRuleConfigView] = None,
    lineupSubmissions: Vector[TournamentLineupSubmissionView]
) derives CanEqual

object TournamentOperationsStageView:
  def apply(
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
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig],
      lineupSubmissions: Vector[TournamentLineupSubmissionView]
  ): TournamentOperationsStageView =
    TournamentOperationsStageView(
      stageId = stageId.value,
      name = name,
      format = format.toString,
      order = order,
      status = status.toString,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount,
      advancementRule = AdvancementRuleView.fromDomain(advancementRule),
      swissRule = swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = knockoutRule.map(KnockoutRuleConfigView.fromDomain),
      lineupSubmissions = lineupSubmissions
    )

final case class TournamentDetailView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: String,
    startsAt: String,
    endsAt: String,
    participatingClubs: Vector[TournamentParticipantClubView],
    participatingPlayers: Vector[TournamentParticipantPlayerView],
    whitelistSummary: TournamentWhitelistSummaryView,
    stages: Vector[TournamentOperationsStageView]
) derives CanEqual

object TournamentDetailView:
  def apply(
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
  ): TournamentDetailView =
    TournamentDetailView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      status = status.toString,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      participatingClubs = participatingClubs,
      participatingPlayers = participatingPlayers,
      whitelistSummary = whitelistSummary,
      stages = stages
    )

final case class TournamentStageSummaryView(
    stageId: String,
    name: String,
    format: String,
    order: Int,
    status: String,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int,
    advancementRule: AdvancementRuleView = AdvancementRuleView.fromDomain(AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured"))),
    swissRule: Option[SwissRuleConfigView] = None,
    knockoutRule: Option[KnockoutRuleConfigView] = None
) derives CanEqual

object TournamentStageSummaryView:
  def apply(
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
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig]
  ): TournamentStageSummaryView =
    TournamentStageSummaryView(
      stageId = stageId.value,
      name = name,
      format = format.toString,
      order = order,
      status = status.toString,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount,
      advancementRule = AdvancementRuleView.fromDomain(advancementRule),
      swissRule = swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = knockoutRule.map(KnockoutRuleConfigView.fromDomain)
    )

  def fromDomain(stage: TournamentStage): TournamentStageSummaryView =
    TournamentStageSummaryView(
      stageId = stage.id.value,
      name = stage.name,
      format = stage.format.toString,
      order = stage.order,
      status = stage.status.toString,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size,
      advancementRule = AdvancementRuleView.fromDomain(stage.advancementRule),
      swissRule = stage.swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = stage.knockoutRule.map(KnockoutRuleConfigView.fromDomain)
    )

final case class TournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    startsAt: String,
    endsAt: String,
    status: String,
    participatingClubIds: Vector[String],
    participatingPlayerIds: Vector[String],
    adminIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[TournamentStageSummaryView]
) derives CanEqual

object TournamentSummaryView:
  def apply(
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
  ): TournamentSummaryView =
    TournamentSummaryView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      status = status.toString,
      participatingClubIds = participatingClubIds.map(_.value),
      participatingPlayerIds = participatingPlayerIds.map(_.value),
      adminIds = adminIds.map(_.value),
      whitelistCount = whitelistCount,
      stages = stages
    )

  def fromDomain(tournament: Tournament): TournamentSummaryView =
    TournamentSummaryView(
      tournamentId = tournament.id.value,
      name = tournament.name,
      organizer = tournament.organizer,
      startsAt = tournament.startsAt.toString,
      endsAt = tournament.endsAt.toString,
      status = tournament.status.toString,
      participatingClubIds = tournament.participatingClubs.map(_.value),
      participatingPlayerIds = tournament.participatingPlayers.map(_.value),
      adminIds = tournament.admins.map(_.value),
      whitelistCount = tournament.whitelist.size,
      stages = tournament.stages.sortBy(_.order).map(TournamentStageSummaryView.fromDomain)
    )

final case class TournamentWhitelistEntryView(
    participantKind: String,
    playerId: Option[String],
    clubId: Option[String]
) derives CanEqual

object TournamentWhitelistEntryView:
  def fromDomain(entry: TournamentWhitelistEntry): TournamentWhitelistEntryView =
    TournamentWhitelistEntryView(
      entry.participantKind.toString,
      entry.playerId.map(_.value),
      entry.clubId.map(_.value)
    )

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
) derives CanEqual
