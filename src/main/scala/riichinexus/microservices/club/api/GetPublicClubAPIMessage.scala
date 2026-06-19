package riichinexus.microservices.club.api
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.ClubHonor
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.{ClubApplicationPolicyView, PublicClubDetailView, PublicClubHonorView}
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.tournamentparticipation.apiTypes.PublicClubLineupMemberView
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.{PublicClubRecentMatchSeatView, PublicClubRecentMatchView}
import riichinexus.microservices.tournament.objects.`private`.{MatchRecordPrivateView, TournamentPrivateView}
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStatus
import riichinexus.microservices.tournament.api.`private`.{ListClubTournamentsPrivateAPIMessage, ListRecentClubMatchRecordsPrivateAPIMessage, ResolveTournamentsPrivateAPIMessage}

import upickle.default.ReadWriter

/** 获取前端公开俱乐部详情。 */
final case class GetPublicClubAPIMessage(
    clubId: String
) extends APIMessage[PublicClubDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicClubDetailView] =
    for
      id <- IO.pure(ClubId(clubId))
      club <- IO.blocking(publicClub(context, id))
      recentRecords <- ListRecentClubMatchRecordsPrivateAPIMessage(club.id, limit = 8).plan(context)
      clubTournaments <- ListClubTournamentsPrivateAPIMessage(club.id).plan(context)
      lineupPlayerIds = currentLineupPlayerIds(club, clubTournaments)
      tournaments <- ResolveTournamentsPrivateAPIMessage(recentRecords.map(_.tournamentId).distinct).plan(context)
      tournamentsById = tournaments.map(tournament => tournament.id -> tournament).toMap
      playersById <- publicClubPlayersById(context, club, lineupPlayerIds, recentRecords)
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
      recentRecords: Vector[MatchRecordPrivateView]
  ): IO[Map[PlayerId, PlayerPrivateView]] =
    ResolvePlayersPrivateAPIMessage(
      (club.members ++ lineupPlayerIds ++ recentRecords.flatMap(_.seatResults.map(_.playerId))).distinct
    ).plan(context)
      .map(_.map(player => player.id -> player).toMap)

  private def publicClubDetailView(
      club: Club,
      lineupPlayerIds: Vector[PlayerId],
      recentRecords: Vector[MatchRecordPrivateView],
      tournamentsById: Map[TournamentId, TournamentPrivateView],
      playersById: Map[PlayerId, PlayerPrivateView]
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
      playersById: Map[PlayerId, PlayerPrivateView]
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
      records: Vector[MatchRecordPrivateView],
      tournamentsById: Map[TournamentId, TournamentPrivateView],
      playersById: Map[PlayerId, PlayerPrivateView]
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

  private def currentLineupPlayerIds(club: Club, tournaments: Vector[TournamentPrivateView]): Vector[PlayerId] =
    latestClubLineupPlayerIds(club, tournaments).getOrElse(club.members)

  private def latestClubLineupPlayerIds(club: Club, tournaments: Vector[TournamentPrivateView]): Option[Vector[PlayerId]] =
    tournaments
      .filter(_.status != TournamentStatus.Draft)
      .flatMap(_.stages)
      .flatMap(_.lineupSubmissions)
      .filter(_.clubId == club.id)
      .sortBy(submission => (submission.submittedAt, submission.id.value))
      .lastOption
      .map(_.seats.filterNot(_.reserve).map(_.playerId))
