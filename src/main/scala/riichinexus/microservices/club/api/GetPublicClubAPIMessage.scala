package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.tournament.objects.RankSnapshotView
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
import upickle.default.*

final case class GetPublicClubAPIMessage(
    clubId: String
) extends APIMessage[PublicClubDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicClubDetailView] =
    for
      id <- IO.blocking(ClubId(clubId))
      club <- IO.blocking(publicClub(context, id))
      recentRecords <- IO.blocking(recentMatchRecords(context, club))
      lineupPlayerIds <- IO.blocking(currentLineupPlayerIds(context, club))
      tournamentsById <- IO.blocking(recentTournamentsById(context, recentRecords))
      playersById <- IO.blocking(publicClubPlayersById(context, club, lineupPlayerIds, recentRecords))
    yield publicClubDetailView(club, lineupPlayerIds, recentRecords, tournamentsById, playersById)

  private def publicClub(context: ApiPlanContext, clubId: ClubId): Club =
    ClubTable
      .findById(context.connection, clubId)
      .filter(_.dissolvedAt.isEmpty)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private def recentMatchRecords(context: ApiPlanContext, club: Club): Vector[MatchRecord] =
    MatchRecordTable.findRecentByClub(context.connection, club.id, limit = 8)

  private def currentLineupPlayerIds(context: ApiPlanContext, club: Club): Vector[PlayerId] =
    latestClubLineupPlayerIds(context, club).getOrElse(club.members)

  private def publicClubPlayersById(
      context: ApiPlanContext,
      club: Club,
      lineupPlayerIds: Vector[PlayerId],
      recentRecords: Vector[MatchRecord]
  ): Map[PlayerId, Player] =
    PlayerTable.findByIds(
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

  private def recentTournamentsById(
      context: ApiPlanContext,
      recentRecords: Vector[MatchRecord]
  ): Map[TournamentId, Tournament] =
    val connection = context.connection
    val tournamentIds = recentRecords.map(_.tournamentId).distinct
    val prefetched = TournamentTable.findByIds(connection, tournamentIds)
      .map(tournament => tournament.id -> tournament)
      .toMap
    tournamentIds.foldLeft(prefetched) { (cached, id) =>
      if cached.contains(id) then cached
      else TournamentTable.findById(connection, id).fold(cached)(tournament => cached.updated(id, tournament))
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

  private def latestClubLineupPlayerIds(context: ApiPlanContext, club: Club): Option[Vector[PlayerId]] =
    TournamentTable.findByClub(context.connection, club.id)
      .filter(_.status != TournamentStatus.Draft)
      .flatMap(_.stages)
      .flatMap(_.lineupSubmissions)
      .filter(_.clubId == club.id)
      .sortBy(submission => (submission.submittedAt, submission.id.value))
      .lastOption
      .map(_.activePlayerIds)
