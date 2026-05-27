package riichinexus.microservices.publicquery.domain

import java.sql.Connection

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.RankSnapshotView
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournament.TournamentTable

object PublicClubDetailQueries:
  def detail(
      connection: Connection,
      module: ClubModuleContext,
      clubId: ClubId
  ): Option[PublicClubDetailView] =
    ClubTable.findById(connection, clubId)
      .filter(_.dissolvedAt.isEmpty)
      .map(club => buildDetail(connection, module, club))

  private def buildDetail(connection: Connection, module: ClubModuleContext, club: Club): PublicClubDetailView =
    val recentRecords = MatchRecordTable.findRecentByClub(connection, club.id, limit = 8)
    val lineupPlayerIds = latestClubLineupPlayerIds(connection, club).getOrElse(club.members)
    val tournamentsById = loadRecentTournaments(connection, recentRecords.map(_.tournamentId).distinct)
    val playersById = PlayerTable.findByIds(
      connection,
      (club.members ++ lineupPlayerIds ++ recentRecords.flatMap(_.seatResults.map(_.playerId))).distinct
    ).map(player => player.id -> player).toMap

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

  private def loadRecentTournaments(
      connection: Connection,
      tournamentIds: Vector[TournamentId]
  ): Map[TournamentId, Tournament] =
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

  private def latestClubLineupPlayerIds(connection: Connection, club: Club): Option[Vector[PlayerId]] =
    TournamentTable.findByClub(connection, club.id)
      .filter(_.status != TournamentStatus.Draft)
      .flatMap(_.stages)
      .flatMap(_.lineupSubmissions)
      .filter(_.clubId == club.id)
      .sortBy(submission => (submission.submittedAt, submission.id.value))
      .lastOption
      .map(_.activePlayerIds)
