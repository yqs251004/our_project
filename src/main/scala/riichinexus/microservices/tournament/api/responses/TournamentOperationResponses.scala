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

final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[Table] = Vector.empty
) derives CanEqual

type TournamentStageDirectoryResponse = TournamentStageDirectoryEntry
type TournamentParticipantClubResponse = TournamentParticipantClubView
type TournamentParticipantPlayerResponse = TournamentParticipantPlayerView
type TournamentWhitelistSummaryResponse = TournamentWhitelistSummaryView
type TournamentLineupSubmissionResponse = TournamentLineupSubmissionView
type TournamentOperationsStageResponse = TournamentOperationsStageView
type TournamentDetailResponse = TournamentDetailView
type TournamentMutationResponse = TournamentMutationView

object TournamentOperationResponses:
  export TournamentResponseCodecs.given
