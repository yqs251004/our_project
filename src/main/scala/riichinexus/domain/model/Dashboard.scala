package riichinexus.domain.model

import java.time.Instant

enum DashboardOwner derives CanEqual:
  case Player(playerId: PlayerId)
  case Club(clubId: ClubId)

final case class Dashboard(
    owner: DashboardOwner,
    sampleSize: Int,
    dealInRate: Double,
    winRate: Double,
    averageWinPoints: Double,
    riichiRate: Double,
    averagePlacement: Double,
    topFinishRate: Double,
    lastUpdatedAt: Instant
) derives CanEqual

object Dashboard:
  def empty(owner: DashboardOwner, at: Instant): Dashboard =
    Dashboard(
      owner = owner,
      sampleSize = 0,
      dealInRate = 0.0,
      winRate = 0.0,
      averageWinPoints = 0.0,
      riichiRate = 0.0,
      averagePlacement = 0.0,
      topFinishRate = 0.0,
      lastUpdatedAt = at
    )

final case class AdvancedStatsBoard(
    owner: DashboardOwner,
    sampleSize: Int,
    defenseStability: Double,
    ukeireExpectation: Double,
    averageShantenImprovement: Double,
    callAggressionRate: Double,
    riichiConversionRate: Double,
    pressureDefenseRate: Double,
    postRiichiFoldRate: Double,
    shantenTrajectory: Vector[Double],
    lastUpdatedAt: Instant
) derives CanEqual

object AdvancedStatsBoard:
  def empty(owner: DashboardOwner, at: Instant): AdvancedStatsBoard =
    AdvancedStatsBoard(
      owner = owner,
      sampleSize = 0,
      defenseStability = 0.0,
      ukeireExpectation = 0.0,
      averageShantenImprovement = 0.0,
      callAggressionRate = 0.0,
      riichiConversionRate = 0.0,
      pressureDefenseRate = 0.0,
      postRiichiFoldRate = 0.0,
      shantenTrajectory = Vector.empty,
      lastUpdatedAt = at
    )

final case class PublicScheduleView(
    tournamentId: TournamentId,
    tournamentName: String,
    tournamentStatus: TournamentStatus,
    stageId: TournamentStageId,
    stageName: String,
    stageStatus: StageStatus,
    startsAt: Instant,
    endsAt: Instant,
    tableCount: Int
) derives CanEqual

final case class PublicClubDirectoryEntry(
    clubId: ClubId,
    name: String,
    memberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    relations: Vector[ClubRelation]
) derives CanEqual

final case class PlayerLeaderboardEntry(
    playerId: PlayerId,
    nickname: String,
    elo: Int,
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
