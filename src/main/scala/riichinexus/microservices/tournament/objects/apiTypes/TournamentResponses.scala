package riichinexus.microservices.tournament.objects.apiTypes

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
    memberCount: Int
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
    submittedBy: PlayerId,
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

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
) derives CanEqual
