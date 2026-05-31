package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.*
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
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.RankSnapshotView
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
    ListPlayersAPIMessage.findPlayersByIds(
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
      clubId = club.id,
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
      relations = club.relations.map(PublicClubRelationView.fromDomain),
      honors = club.honors.sortBy(honor => (honor.achievedAt, honor.title)).reverse.map(PublicClubHonorView.fromDomain),
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
        val privilegeSnapshot = club.memberPrivilegeSnapshot(player.id)
        PublicClubLineupMemberView(
          playerId = player.id,
          nickname = player.nickname,
          elo = player.elo,
          currentRank = RankSnapshotView.fromDomain(player.currentRank),
          status = player.status,
          isAdmin = club.admins.contains(player.id),
          internalTitle = privilegeSnapshot.flatMap(_.internalTitle),
          privileges = privilegeSnapshot.map(_.privileges).getOrElse(Vector.empty)
        )
      }

  private def recentMatches(
      records: Vector[MatchRecord],
      tournamentsById: Map[TournamentId, Tournament],
      playersById: Map[PlayerId, Player]
  ): Vector[PublicClubRecentMatchView] =
    records.map { record =>
      val tournament = tournamentsById.get(record.tournamentId)
      PublicClubRecentMatchView(
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
            PublicClubRecentMatchSeatView(
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

  private def clubApplicationPolicy(club: Club): ClubApplicationPolicyView =
    val applicationsOpen = club.dissolvedAt.isEmpty && club.recruitmentPolicy.applicationsOpen
    ClubApplicationPolicyView(
      applicationsOpen = applicationsOpen,
      requirementsText =
        if applicationsOpen then club.recruitmentPolicy.requirementsText else None,
      expectedReviewSlaHours =
        if applicationsOpen then club.recruitmentPolicy.expectedReviewSlaHours else None,
      pendingApplicationCount = club.membershipApplications.count(_.isPending)
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
