package riichinexus.microservices.publicquery.api.responses

import java.time.Instant

import riichinexus.domain.model.*

final case class PublicScheduleView(
    tournamentId: TournamentId,
    tournamentName: String,
    tournamentStatus: TournamentStatus,
    stageId: TournamentStageId,
    stageName: String,
    stageStatus: StageStatus,
    currentRound: Int,
    roundCount: Int,
    startsAt: Instant,
    endsAt: Instant,
    tableCount: Int,
    activeTableCount: Int,
    pendingTablePlanCount: Int,
    participantCount: Int,
    whitelistCount: Int
) derives CanEqual

final case class PublicClubDirectoryEntry(
    clubId: ClubId,
    name: String,
    memberCount: Int,
    activeMemberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    allianceCount: Int,
    rivalryCount: Int,
    strongestRivalClubId: Option[ClubId],
    strongestRivalPower: Option[Double],
    honorTitles: Vector[String],
    relations: Vector[ClubRelation]
) derives CanEqual

final case class PlayerLeaderboardEntry(
    playerId: PlayerId,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    normalizedRankScore: Option[Int],
    clubIds: Vector[ClubId],
    status: PlayerStatus
) derives CanEqual

final case class ClubLeaderboardEntry(
    clubId: ClubId,
    name: String,
    powerRating: Double,
    totalPoints: Int,
    memberCount: Int
) derives CanEqual

final case class ClubApplicationPolicyView(
    applicationsOpen: Boolean,
    requirementsText: Option[String],
    expectedReviewSlaHours: Option[Int],
    pendingApplicationCount: Int
) derives CanEqual

final case class PublicClubLineupMemberView(
    playerId: PlayerId,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    status: PlayerStatus,
    isAdmin: Boolean,
    internalTitle: Option[String],
    privileges: Vector[String]
) derives CanEqual

final case class PublicClubRecentMatchSeatView(
    playerId: PlayerId,
    nickname: String,
    clubId: Option[ClubId],
    seat: SeatWind,
    placement: Int,
    scoreDelta: Int,
    finalPoints: Int
) derives CanEqual

final case class PublicClubRecentMatchView(
    matchRecordId: MatchRecordId,
    tournamentId: TournamentId,
    tournamentName: String,
    stageId: TournamentStageId,
    stageName: String,
    tableId: TableId,
    generatedAt: Instant,
    seats: Vector[PublicClubRecentMatchSeatView]
) derives CanEqual

final case class PublicClubDetailView(
    clubId: ClubId,
    name: String,
    memberCount: Int,
    activeMemberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    relations: Vector[ClubRelation],
    honors: Vector[ClubHonor],
    applicationPolicy: ClubApplicationPolicyView,
    currentLineup: Vector[PublicClubLineupMemberView],
    recentMatches: Vector[PublicClubRecentMatchView]
) derives CanEqual

final case class PublicTournamentSummaryView(
    tournamentId: TournamentId,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: Instant,
    endsAt: Instant,
    stageCount: Int,
    activeStageCount: Int,
    participantCount: Int,
    clubCount: Int,
    playerCount: Int
) derives CanEqual

final case class PublicTournamentStageView(
    stageId: TournamentStageId,
    name: String,
    format: StageFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    tableCount: Int,
    archivedTableCount: Int,
    pendingTablePlanCount: Int,
    standings: Option[StageRankingSnapshot],
    bracket: Option[KnockoutBracketSnapshot]
) derives CanEqual

final case class PublicTournamentDetailView(
    tournamentId: TournamentId,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: Instant,
    endsAt: Instant,
    clubIds: Vector[ClubId],
    playerIds: Vector[PlayerId],
    whitelistCount: Int,
    stages: Vector[PublicTournamentStageView]
) derives CanEqual

object PublicQueryResponses:
  type PublicScheduleResponse = PublicScheduleView
  type PublicClubDirectoryEntryResponse = PublicClubDirectoryEntry
  type PublicPlayerLeaderboardEntryResponse = PlayerLeaderboardEntry
  type PublicClubLeaderboardEntryResponse = ClubLeaderboardEntry
  type PublicClubDetailResponse = PublicClubDetailView
  type PublicTournamentSummaryResponse = PublicTournamentSummaryView
  type PublicTournamentStageResponse = PublicTournamentStageView
  type PublicTournamentDetailResponse = PublicTournamentDetailView

  export PublicQueryResponseCodecs.given
