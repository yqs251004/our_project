package riichinexus.microservices.club.api
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.*
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.*
import riichinexus.microservices.club.objects.relationmanagement.apiTypes.*
import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.*
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.StageLineupSubmissionFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStatus
import riichinexus.microservices.tournament.api.`private`.{
  ListClubTournamentsPrivateAPIMessage,
  ListRecentClubMatchRecordsPrivateAPIMessage,
  ResolveTournamentsPrivateAPIMessage
}
import upickle.default.*

final case class GetPublicClubAPIMessage(
    clubId: String
) extends APIMessage[PublicClubDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicClubDetailView] =
    for
      id <- IO.blocking(ClubId(clubId))
      club <- IO.blocking(publicClub(context, id))
      recentRecords <- ListRecentClubMatchRecordsPrivateAPIMessage(club.id, limit = 8).plan(context)
      clubTournaments <- ListClubTournamentsPrivateAPIMessage(club.id).plan(context)
      lineupPlayerIds <- IO.blocking(currentLineupPlayerIds(club, clubTournaments))
      tournaments <- ResolveTournamentsPrivateAPIMessage(recentRecords.map(_.tournamentId).distinct).plan(context)
      tournamentsById <- IO.blocking(tournaments.map(tournament => tournament.id -> tournament).toMap)
      playersById <- IO.blocking(publicClubPlayersById(context, club, lineupPlayerIds, recentRecords))
    yield publicClubDetailView(club, lineupPlayerIds, recentRecords, tournamentsById, playersById)

  private def publicClub(context: ApiPlanContext, clubId: ClubId): Club =
    ClubTable
      .findById(context.connection, clubId)
      .filter(_.dissolvedAt.isEmpty)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private def publicClubPlayersById(
      context: ApiPlanContext,
      club: Club,
      lineupPlayerIds: Vector[PlayerId],
      recentRecords: Vector[MatchRecord]
  ): Map[PlayerId, Player] =
    PlayerPersistenceFunctions.findPlayersByIds(
      context.connection,
      (club.members ++ lineupPlayerIds ++ recentRecords.flatMap(_.seatResults.map(_.playerId))).distinct
    ).map(player => player.id -> player).toMap

  private def publicClubDetailView(
      club: Club,
      lineupPlayerIds: Vector[PlayerId],
      recentRecords: Vector[MatchRecord],
      tournamentsById: Map[TournamentId, Tournament],
      playersById: Map[PlayerId, Player]
  ): PublicClubDetailView =
    PublicClubDetailView(
      clubId = club.id.value,
      name = club.name,
      memberCount = club.members.size,
      activeMemberCount = club.members.count(playerId =>
        playersById.get(playerId).exists(_.status == PlayerStatus.Active)
      ),
      adminCount = club.admins.size,
      powerRating = club.powerRating,
      totalPoints = club.totalPoints,
      treasuryBalance = club.treasuryBalance,
      pointPool = club.pointPool,
      relations = club.relations.map(ClubRelationView.fromDomain),
      honors = club.honors.sortBy(honor => (honor.achievedAt, honor.title)).reverse.map(publicClubHonorView),
      applicationPolicy = clubApplicationPolicy(club),
      currentLineup = currentLineup(club, lineupPlayerIds, playersById),
      recentMatches = recentMatches(recentRecords, tournamentsById, playersById)
    )

  private def currentLineup(
      club: Club,
      lineupPlayerIds: Vector[PlayerId],
      playersById: Map[PlayerId, Player]
  ): Vector[PublicClubLineupMemberView] =
    lineupPlayerIds
      .flatMap(playersById.get)
      .sortBy(player => (-player.elo, player.nickname, player.id.value))
      .map { player =>
        val privilegeSnapshot = ClubFunctions.memberPrivilegeSnapshot(club, player.id)
        publicClubLineupMemberView(
          playerId = player.id,
          nickname = player.nickname,
          elo = player.elo,
          currentRank = player.currentRank,
          status = player.status,
          isAdmin = club.admins.contains(player.id),
          internalTitle = privilegeSnapshot.flatMap(_.internalTitle),
          privileges = privilegeSnapshot.map(_.privileges).getOrElse(Vector.empty)
        )
      }

  private def publicClubLineupMemberView(
      playerId: PlayerId,
      nickname: String,
      elo: Int,
      currentRank: RankSnapshot,
      status: PlayerStatus,
      isAdmin: Boolean,
      internalTitle: Option[String],
      privileges: Vector[ClubPrivilegeCode]
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

  private def recentMatches(
      records: Vector[MatchRecord],
      tournamentsById: Map[TournamentId, Tournament],
      playersById: Map[PlayerId, Player]
  ): Vector[PublicClubRecentMatchView] =
    records.map { record =>
      val tournament = tournamentsById.get(record.tournamentId)
      publicClubRecentMatchView(
        matchRecordId = record.id,
        tournamentId = record.tournamentId,
        tournamentName = tournament.map(_.name).getOrElse(record.tournamentId.value),
        stageId = record.stageId,
        stageName = tournament
          .flatMap(_.stages.find(_.id == record.stageId))
          .map(_.name)
          .getOrElse(record.stageId.value),
        tableId = record.tableId,
        generatedAt = record.generatedAt,
        seats = record.seatResults
          .sortBy(_.placement)
          .map { result =>
            publicClubRecentMatchSeatView(
              playerId = result.playerId,
              nickname = playersById.get(result.playerId).map(_.nickname).getOrElse(result.playerId.value),
              clubId = result.clubId,
              seat = result.seat.toString,
              placement = result.placement,
              scoreDelta = result.scoreDelta,
              finalPoints = result.finalPoints
            )
          }
      )
    }

  private def publicClubRecentMatchView(
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

  private def publicClubRecentMatchSeatView(
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

  private def publicClubHonorView(honor: ClubHonor): PublicClubHonorView =
    PublicClubHonorView(title = honor.title)

  private def clubApplicationPolicy(club: Club): ClubApplicationPolicyView =
    val applicationsOpen = club.dissolvedAt.isEmpty && club.recruitmentPolicy.applicationsOpen
    ClubApplicationPolicyView(
      applicationsOpen = applicationsOpen,
      requirementsText =
        if applicationsOpen then club.recruitmentPolicy.requirementsText else None,
      expectedReviewSlaHours =
        if applicationsOpen then club.recruitmentPolicy.expectedReviewSlaHours else None,
      pendingApplicationCount = club.membershipApplications.count(ClubMembershipApplicationFunctions.isPending)
    )

  private def currentLineupPlayerIds(club: Club, tournaments: Vector[Tournament]): Vector[PlayerId] =
    latestClubLineupPlayerIds(club, tournaments).getOrElse(club.members)

  private def latestClubLineupPlayerIds(club: Club, tournaments: Vector[Tournament]): Option[Vector[PlayerId]] =
    tournaments
      .filter(_.status != TournamentStatus.Draft)
      .flatMap(_.stages)
      .flatMap(_.lineupSubmissions)
      .filter(_.clubId == club.id)
      .sortBy(submission => (submission.submittedAt, submission.id.value))
      .lastOption
      .map(StageLineupSubmissionFunctions.activePlayerIds)
