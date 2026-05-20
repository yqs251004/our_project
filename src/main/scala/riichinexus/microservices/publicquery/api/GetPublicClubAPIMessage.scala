package riichinexus.microservices.publicquery.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.objects.apiTypes.*
import upickle.default.*

final case class GetPublicClubAPIMessage(
    clubId: String
) extends APIMessage[PublicClubDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicClubDetailView] =
    IO {
      buildPublicClubDetailView(context, ClubId(clubId))
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def buildPublicClubDetailView(context: ApiPlanContext, clubId: ClubId): Option[PublicClubDetailView] =
    val tables = context.support.clubModule.tables
    tables.findClub(clubId)
      .filter(_.dissolvedAt.isEmpty)
      .map { club =>
        val recentRecords = tables.listRecentMatchRecordsByClub(club.id, limit = 8)
        val lineupPlayerIds = latestClubLineupPlayerIds(context, club).getOrElse(club.members)
        val recentTournamentIds = recentRecords.map(_.tournamentId).distinct
        val tournamentsById = tables.findTournaments(recentTournamentIds)
          .map(tournament => tournament.id -> tournament)
          .toMap
        val playersById = loadPlayersById(
          context,
          (club.members ++ lineupPlayerIds ++ recentRecords.flatMap(_.seatResults.map(_.playerId))).distinct
        )
        val tournamentCache = scala.collection.mutable.Map.empty[TournamentId, Option[Tournament]]
        def tournamentFor(id: TournamentId): Option[Tournament] =
          tournamentCache.getOrElseUpdate(id, tournamentsById.get(id).orElse(tables.findTournament(id)))
        def nicknameFor(id: PlayerId): String =
          playersById.get(id).map(_.nickname).getOrElse(id.value)

        val currentLineup = lineupPlayerIds
          .flatMap(playerId => playersById.get(playerId))
          .sortBy(player => (-player.elo, player.nickname, player.id.value))
          .map { player =>
            val privilegeSnapshot = club.memberPrivilegeSnapshot(player.id)
            PublicClubLineupMemberView(
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

        val recentMatches = recentRecords.map { record =>
          val tournamentName = tournamentFor(record.tournamentId).map(_.name).getOrElse(record.tournamentId.value)
          val stageName = tournamentFor(record.tournamentId)
            .flatMap(_.stages.find(_.id == record.stageId))
            .map(_.name)
            .getOrElse(record.stageId.value)
          PublicClubRecentMatchView(
            matchRecordId = record.id,
            tournamentId = record.tournamentId,
            tournamentName = tournamentName,
            stageId = record.stageId,
            stageName = stageName,
            tableId = record.tableId,
            generatedAt = record.generatedAt,
            seats = record.seatResults
              .sortBy(_.placement)
              .map { result =>
                PublicClubRecentMatchSeatView(
                  playerId = result.playerId,
                  nickname = nicknameFor(result.playerId),
                  clubId = result.clubId,
                  seat = result.seat,
                  placement = result.placement,
                  scoreDelta = result.scoreDelta,
                  finalPoints = result.finalPoints
                )
              }
          )
        }

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
          relations = club.relations,
          honors = club.honors.sortBy(honor => (honor.achievedAt, honor.title)).reverse,
          applicationPolicy = clubApplicationPolicy(club),
          currentLineup = currentLineup,
          recentMatches = recentMatches
        )
      }

  private def loadPlayersById(context: ApiPlanContext, playerIds: Iterable[PlayerId]): Map[PlayerId, Player] =
    context.support.clubModule.tables.findPlayers(playerIds)
      .map(player => player.id -> player)
      .toMap

  private def clubApplicationsOpen(club: Club): Boolean =
    club.dissolvedAt.isEmpty && club.recruitmentPolicy.applicationsOpen

  private def clubApplicationPolicy(club: Club): ClubApplicationPolicyView =
    ClubApplicationPolicyView(
      applicationsOpen = clubApplicationsOpen(club),
      requirementsText =
        if clubApplicationsOpen(club) then club.recruitmentPolicy.requirementsText else None,
      expectedReviewSlaHours =
        if clubApplicationsOpen(club) then club.recruitmentPolicy.expectedReviewSlaHours else None,
      pendingApplicationCount = club.membershipApplications.count(_.isPending)
    )

  private def latestClubLineupPlayerIds(context: ApiPlanContext, club: Club): Option[Vector[PlayerId]] =
    context.support.clubModule.tables.listTournamentsByClub(club.id)
      .filter(_.status != TournamentStatus.Draft)
      .flatMap(_.stages)
      .flatMap(_.lineupSubmissions)
      .filter(_.clubId == club.id)
      .sortBy(submission => (submission.submittedAt, submission.id.value))
      .lastOption
      .map(_.activePlayerIds)
