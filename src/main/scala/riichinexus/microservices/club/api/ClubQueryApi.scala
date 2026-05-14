package riichinexus.microservices.club.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.club.objects.*
import riichinexus.microservices.club.tables.ClubTables

object ClubQueryApi:
  private val RecentTournamentWindow = Duration.ofDays(90)

  def privilegeDefinitions: Vector[ClubPrivilegeDefinition] =
    ClubPrivilegeRegistry.definitions

  def listClubs(
      tables: ClubTables,
      query: ClubListQuery
  ): Vector[Club] =
    tables.listClubs(
      activeOnly = query.activeOnly,
      joinableOnly = query.joinableOnly,
      memberId = query.memberId,
      adminId = query.adminId,
      name = query.name
    )
      .sortBy(club => (club.dissolvedAt.nonEmpty, club.name, club.id.value))

  def findClub(
      tables: ClubTables,
      clubId: ClubId
  ): Option[Club] =
    tables.findClub(clubId)

  def clubTournamentParticipations(
      tables: ClubTables,
      views: ClubViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      clubId: ClubId,
      query: ClubTournamentParticipationQuery
  ): Vector[ClubTournamentParticipationView] =
    tables
      .findClub(clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
    val viewer = query.viewer.map(principalOf).getOrElse(AccessPrincipal.guest())
    val items = tables.listTournamentsByClub(clubId)
      .flatMap(tournament => views.buildClubTournamentParticipationView(clubId, tournament, viewer))
    val recentThreshold = Instant.now().minus(RecentTournamentWindow)

    val filtered =
      query.scope.trim.toLowerCase match
        case "recent" =>
          items.filter(item =>
            item.status == TournamentStatus.RegistrationOpen ||
              item.status == TournamentStatus.Scheduled ||
              item.status == TournamentStatus.InProgress ||
              item.endsAt.isAfter(recentThreshold)
          )
        case "active" =>
          items.filter(item =>
            item.status == TournamentStatus.RegistrationOpen ||
              item.status == TournamentStatus.Scheduled ||
              item.status == TournamentStatus.InProgress
          )
        case "all" => items
        case other =>
          throw IllegalArgumentException(
            s"Unsupported scope '$other'. Supported values: recent, active, all"
          )

    filtered.sortBy(item => (item.startsAt, item.tournamentId.value)).reverse

  def listMembers(
      tables: ClubTables,
      containsIgnoreCase: (String, String) => Boolean,
      clubId: ClubId,
      query: ClubMemberQuery
  ): Vector[Player] =
    tables.listPlayersByClub(clubId)
      .filter(player => query.status.forall(_ == player.status))
      .filter(player => query.nickname.forall(containsIgnoreCase(player.nickname, _)))
      .sortBy(player => (player.nickname, player.id.value))

  def listPrivilegeSnapshots(
      tables: ClubTables,
      clubId: ClubId,
      query: ClubPrivilegeSnapshotQuery
  ): Vector[ClubMemberPrivilegeSnapshot] =
    tables.listMemberPrivilegeSnapshots(clubId)
      .filter(snapshot => query.playerId.forall(_ == snapshot.playerId))
      .filter(snapshot => query.privilege.forall(snapshot.privileges.contains))
      .filter(snapshot => query.rankCode.forall(_ == snapshot.rankCode.trim.toLowerCase))

  def privilegeSnapshot(
      tables: ClubTables,
      clubId: ClubId,
      playerId: PlayerId
  ): Option[ClubMemberPrivilegeSnapshot] =
    tables.memberPrivilegeSnapshot(clubId, playerId)
