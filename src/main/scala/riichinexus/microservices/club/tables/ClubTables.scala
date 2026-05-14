package riichinexus.microservices.club.tables

import java.util.NoSuchElementException

import riichinexus.application.ports.{ClubRepository, MatchRecordRepository, PlayerRepository, TournamentRepository}
import riichinexus.domain.model.*

final class ClubTables(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    tournamentRepository: TournamentRepository,
    matchRecordRepository: MatchRecordRepository
):
  def listClubs(
      activeOnly: Boolean,
      joinableOnly: Boolean,
      memberId: Option[PlayerId],
      adminId: Option[PlayerId],
      name: Option[String]
  ): Vector[Club] =
    clubRepository.findFiltered(
      activeOnly = activeOnly,
      joinableOnly = joinableOnly,
      memberId = memberId,
      adminId = adminId,
      name = name
    )

  def findClub(clubId: ClubId): Option[Club] =
    clubRepository.findById(clubId)

  def memberPrivilegeSnapshot(
      clubId: ClubId,
      playerId: PlayerId
  ): Option[ClubMemberPrivilegeSnapshot] =
    clubRepository.findById(clubId).flatMap { club =>
      ensureClubActive(club)
      club.memberPrivilegeSnapshot(playerId)
    }

  def listMemberPrivilegeSnapshots(clubId: ClubId): Vector[ClubMemberPrivilegeSnapshot] =
    clubRepository.findById(clubId).map { club =>
      ensureClubActive(club)
      club.memberPrivilegeSnapshots
    }.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  def findPlayers(playerIds: Iterable[PlayerId]): Vector[Player] =
    playerRepository.findByIds(playerIds.toVector.distinct)

  def listPlayersByClub(clubId: ClubId): Vector[Player] =
    playerRepository.findByClub(clubId)

  def findPlayer(playerId: PlayerId): Option[Player] =
    playerRepository.findById(playerId)

  def findPlayerByUserId(userId: String): Option[Player] =
    playerRepository.findByUserId(userId)

  def findTournaments(tournamentIds: Iterable[TournamentId]): Vector[Tournament] =
    tournamentRepository.findByIds(tournamentIds.toVector.distinct)

  def findTournament(tournamentId: TournamentId): Option[Tournament] =
    tournamentRepository.findById(tournamentId)

  def listTournamentsByClub(clubId: ClubId): Vector[Tournament] =
    tournamentRepository.findByClub(clubId)

  def listRecentMatchRecordsByClub(clubId: ClubId, limit: Int): Vector[MatchRecord] =
    matchRecordRepository.findRecentByClub(clubId, limit)

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
