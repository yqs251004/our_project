package riichinexus.microservices.publicquery.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.{
  AdvancementRuleView,
  KnockoutBracketSnapshot,
  KnockoutRuleConfigView,
  StageRankingSnapshot,
  SwissRuleConfigView
}
import upickle.default.*

final case class PublicClubRelationView(
    relation: String
) derives CanEqual

object PublicClubRelationView:
  def fromDomain(relation: ClubRelation): PublicClubRelationView =
    PublicClubRelationView(relation = relation.relation.toString)

final case class PublicClubHonorView(
    title: String
) derives CanEqual

object PublicClubHonorView:
  def fromDomain(honor: ClubHonor): PublicClubHonorView =
    PublicClubHonorView(title = honor.title)

final case class RankSnapshotView(
    platform: String,
    tier: String,
    stars: Option[Int]
) derives CanEqual

object RankSnapshotView:
  def fromDomain(rank: RankSnapshot): RankSnapshotView =
    RankSnapshotView(
      platform = rank.platform.toString,
      tier = rank.tier,
      stars = rank.stars
    )

final case class PublicScheduleView(
    tournamentId: String,
    tournamentName: String,
    tournamentStatus: String,
    stageId: String,
    stageName: String,
    stageStatus: String,
    currentRound: Int,
    roundCount: Int,
    startsAt: String,
    endsAt: String,
    tableCount: Int,
    activeTableCount: Int,
    pendingTablePlanCount: Int,
    participantCount: Int,
    whitelistCount: Int
) derives CanEqual

object PublicScheduleView:
  def apply(
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
  ): PublicScheduleView =
    PublicScheduleView(
      tournamentId = tournamentId.value,
      tournamentName = tournamentName,
      tournamentStatus = tournamentStatus.toString,
      stageId = stageId.value,
      stageName = stageName,
      stageStatus = stageStatus.toString,
      currentRound = currentRound,
      roundCount = roundCount,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      tableCount = tableCount,
      activeTableCount = activeTableCount,
      pendingTablePlanCount = pendingTablePlanCount,
      participantCount = participantCount,
      whitelistCount = whitelistCount
    )

final case class PublicClubDirectoryEntry(
    clubId: String,
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
    strongestRivalClubId: Option[String],
    strongestRivalPower: Option[Double],
    honorTitles: Vector[String],
    relations: Vector[PublicClubRelationView]
) derives CanEqual

object PublicClubDirectoryEntry:
  def apply(
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
      relations: Vector[PublicClubRelationView]
  ): PublicClubDirectoryEntry =
    PublicClubDirectoryEntry(
      clubId = clubId.value,
      name = name,
      memberCount = memberCount,
      activeMemberCount = activeMemberCount,
      adminCount = adminCount,
      powerRating = powerRating,
      totalPoints = totalPoints,
      treasuryBalance = treasuryBalance,
      pointPool = pointPool,
      allianceCount = allianceCount,
      rivalryCount = rivalryCount,
      strongestRivalClubId = strongestRivalClubId.map(_.value),
      strongestRivalPower = strongestRivalPower,
      honorTitles = honorTitles,
      relations = relations
    )

final case class PlayerLeaderboardEntry(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshotView,
    normalizedRankScore: Option[Int],
    clubIds: Vector[String],
    status: String
) derives CanEqual

object PlayerLeaderboardEntry:
  def apply(
      playerId: PlayerId,
      nickname: String,
      elo: Int,
      currentRank: RankSnapshotView,
      normalizedRankScore: Option[Int],
      clubIds: Vector[ClubId],
      status: PlayerStatus
  ): PlayerLeaderboardEntry =
    PlayerLeaderboardEntry(
      playerId = playerId.value,
      nickname = nickname,
      elo = elo,
      currentRank = currentRank,
      normalizedRankScore = normalizedRankScore,
      clubIds = clubIds.map(_.value),
      status = status.toString
    )

final case class ClubLeaderboardEntry(
    clubId: String,
    name: String,
    powerRating: Double,
    totalPoints: Int,
    memberCount: Int
) derives CanEqual

object ClubLeaderboardEntry:
  def apply(
      clubId: ClubId,
      name: String,
      powerRating: Double,
      totalPoints: Int,
      memberCount: Int
  ): ClubLeaderboardEntry =
    ClubLeaderboardEntry(clubId.value, name, powerRating, totalPoints, memberCount)

final case class ClubApplicationPolicyView(
    applicationsOpen: Boolean,
    requirementsText: Option[String],
    expectedReviewSlaHours: Option[Int],
    pendingApplicationCount: Int
) derives CanEqual

final case class PublicClubLineupMemberView(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshotView,
    status: String,
    isAdmin: Boolean,
    internalTitle: Option[String],
    privileges: Vector[String]
) derives CanEqual

object PublicClubLineupMemberView:
  def apply(
      playerId: PlayerId,
      nickname: String,
      elo: Int,
      currentRank: RankSnapshotView,
      status: PlayerStatus,
      isAdmin: Boolean,
      internalTitle: Option[String],
      privileges: Vector[String]
  ): PublicClubLineupMemberView =
    PublicClubLineupMemberView(
      playerId = playerId.value,
      nickname = nickname,
      elo = elo,
      currentRank = currentRank,
      status = status.toString,
      isAdmin = isAdmin,
      internalTitle = internalTitle,
      privileges = privileges
    )

final case class PublicClubRecentMatchSeatView(
    playerId: String,
    nickname: String,
    clubId: Option[String],
    seat: String,
    placement: Int,
    scoreDelta: Int,
    finalPoints: Int
) derives CanEqual

object PublicClubRecentMatchSeatView:
  def apply(
      playerId: PlayerId,
      nickname: String,
      clubId: Option[ClubId],
      seat: String,
      placement: Int,
      scoreDelta: Int,
      finalPoints: Int
  ): PublicClubRecentMatchSeatView =
    PublicClubRecentMatchSeatView(
      playerId = playerId.value,
      nickname = nickname,
      clubId = clubId.map(_.value),
      seat = seat,
      placement = placement,
      scoreDelta = scoreDelta,
      finalPoints = finalPoints
    )

final case class PublicClubRecentMatchView(
    matchRecordId: String,
    tournamentId: String,
    tournamentName: String,
    stageId: String,
    stageName: String,
    tableId: String,
    generatedAt: String,
    seats: Vector[PublicClubRecentMatchSeatView]
) derives CanEqual

object PublicClubRecentMatchView:
  def apply(
      matchRecordId: MatchRecordId,
      tournamentId: TournamentId,
      tournamentName: String,
      stageId: TournamentStageId,
      stageName: String,
      tableId: TableId,
      generatedAt: Instant,
      seats: Vector[PublicClubRecentMatchSeatView]
  ): PublicClubRecentMatchView =
    PublicClubRecentMatchView(
      matchRecordId = matchRecordId.value,
      tournamentId = tournamentId.value,
      tournamentName = tournamentName,
      stageId = stageId.value,
      stageName = stageName,
      tableId = tableId.value,
      generatedAt = generatedAt.toString,
      seats = seats
    )

final case class PublicClubDetailView(
    clubId: String,
    name: String,
    memberCount: Int,
    activeMemberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    relations: Vector[PublicClubRelationView],
    honors: Vector[PublicClubHonorView],
    applicationPolicy: ClubApplicationPolicyView,
    currentLineup: Vector[PublicClubLineupMemberView],
    recentMatches: Vector[PublicClubRecentMatchView]
) derives CanEqual

object PublicClubDetailView:
  def apply(
      clubId: ClubId,
      name: String,
      memberCount: Int,
      activeMemberCount: Int,
      adminCount: Int,
      powerRating: Double,
      totalPoints: Int,
      treasuryBalance: Long,
      pointPool: Int,
      relations: Vector[PublicClubRelationView],
      honors: Vector[PublicClubHonorView],
      applicationPolicy: ClubApplicationPolicyView,
      currentLineup: Vector[PublicClubLineupMemberView],
      recentMatches: Vector[PublicClubRecentMatchView]
  ): PublicClubDetailView =
    PublicClubDetailView(
      clubId = clubId.value,
      name = name,
      memberCount = memberCount,
      activeMemberCount = activeMemberCount,
      adminCount = adminCount,
      powerRating = powerRating,
      totalPoints = totalPoints,
      treasuryBalance = treasuryBalance,
      pointPool = pointPool,
      relations = relations,
      honors = honors,
      applicationPolicy = applicationPolicy,
      currentLineup = currentLineup,
      recentMatches = recentMatches
    )

final case class PublicTournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: String,
    startsAt: String,
    endsAt: String,
    stageCount: Int,
    activeStageCount: Int,
    participantCount: Int,
    clubCount: Int,
    playerCount: Int
) derives CanEqual

object PublicTournamentSummaryView:
  def apply(
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
  ): PublicTournamentSummaryView =
    PublicTournamentSummaryView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      status = status.toString,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      stageCount = stageCount,
      activeStageCount = activeStageCount,
      participantCount = participantCount,
      clubCount = clubCount,
      playerCount = playerCount
    )

final case class PublicTournamentStageView(
    stageId: String,
    name: String,
    format: String,
    order: Int,
    status: String,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    tableCount: Int,
    archivedTableCount: Int,
    pendingTablePlanCount: Int,
    standings: Option[StageRankingSnapshot],
    bracket: Option[KnockoutBracketSnapshot],
    advancementRule: AdvancementRuleView = AdvancementRuleView.fromDomain(AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured"))),
    swissRule: Option[SwissRuleConfigView] = None,
    knockoutRule: Option[KnockoutRuleConfigView] = None
) derives CanEqual

object PublicTournamentStageView:
  def apply(
      stageId: TournamentStageId,
      name: String,
      format: StageFormat,
      order: Int,
      status: StageStatus,
      currentRound: Int,
      roundCount: Int,
      schedulingPoolSize: Int,
      tableCount: Int,
      archivedTableCount: Int,
      pendingTablePlanCount: Int,
      standings: Option[StageRankingSnapshot],
      bracket: Option[KnockoutBracketSnapshot],
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig]
  ): PublicTournamentStageView =
    PublicTournamentStageView(
      stageId = stageId.value,
      name = name,
      format = format.toString,
      order = order,
      status = status.toString,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      tableCount = tableCount,
      archivedTableCount = archivedTableCount,
      pendingTablePlanCount = pendingTablePlanCount,
      standings = standings,
      bracket = bracket,
      advancementRule = AdvancementRuleView.fromDomain(advancementRule),
      swissRule = swissRule.map(SwissRuleConfigView.fromDomain),
      knockoutRule = knockoutRule.map(KnockoutRuleConfigView.fromDomain)
    )

final case class PublicTournamentDetailView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: String,
    startsAt: String,
    endsAt: String,
    clubIds: Vector[String],
    playerIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[PublicTournamentStageView]
) derives CanEqual

object PublicTournamentDetailView:
  def apply(
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
  ): PublicTournamentDetailView =
    PublicTournamentDetailView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      status = status.toString,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      clubIds = clubIds.map(_.value),
      playerIds = playerIds.map(_.value),
      whitelistCount = whitelistCount,
      stages = stages
    )

object PublicQueryResponses:
  type PublicScheduleResponse = PublicScheduleView
  type PublicClubDirectoryEntryResponse = PublicClubDirectoryEntry
  type PublicPlayerLeaderboardEntryResponse = PlayerLeaderboardEntry
  type PublicClubLeaderboardEntryResponse = ClubLeaderboardEntry
  type PublicClubDetailResponse = PublicClubDetailView
  type PublicTournamentSummaryResponse = PublicTournamentSummaryView
  type PublicTournamentStageResponse = PublicTournamentStageView
  type PublicTournamentDetailResponse = PublicTournamentDetailView

  given ReadWriter[PublicScheduleView] = macroRW
  given ReadWriter[PublicClubRelationView] = macroRW
  given ReadWriter[PublicClubHonorView] = macroRW
  given ReadWriter[RankSnapshotView] = macroRW
  given ReadWriter[PublicClubDirectoryEntry] = macroRW
  given ReadWriter[PlayerLeaderboardEntry] = macroRW
  given ReadWriter[ClubLeaderboardEntry] = macroRW
  given ReadWriter[ClubApplicationPolicyView] = macroRW
  given ReadWriter[PublicClubLineupMemberView] = macroRW
  given ReadWriter[PublicClubRecentMatchSeatView] = macroRW
  given ReadWriter[PublicClubRecentMatchView] = macroRW
  given ReadWriter[PublicClubDetailView] = macroRW
  given ReadWriter[PublicTournamentSummaryView] = macroRW
  given ReadWriter[PublicTournamentStageView] = macroRW
  given ReadWriter[PublicTournamentDetailView] = macroRW
