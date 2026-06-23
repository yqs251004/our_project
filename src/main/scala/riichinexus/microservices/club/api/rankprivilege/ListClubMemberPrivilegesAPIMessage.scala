package riichinexus.microservices.club.api.rankprivilege
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import riichinexus.microservices.club.domain.rankprivilege.model.ClubMemberPrivilegeSnapshot

import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.microservices.club.objects.rankprivilege.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.objects.rankprivilege.apiTypes.ClubMemberPrivilegeListQuery
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出俱乐部成员权限。 */
final case class ListClubMemberPrivilegesAPIMessage(
    clubId: String,
    query: ClubMemberPrivilegeListQuery = ClubMemberPrivilegeListQuery()
) extends APIMessage[PagedResponse[ClubMemberPrivilegeSnapshotView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMemberPrivilegeSnapshotView]] =
    val requestedClubId = ClubId(clubId)
    val requestedPlayerId = query.playerId.filter(_.nonEmpty).map(PlayerId(_))
    val rankCodeFilter = query.rankCode.filter(_.nonEmpty).map(_.trim.toLowerCase)
    val resolvedLimit = query.limit.getOrElse(20)
    val resolvedOffset = query.offset.getOrElse(0)
    val appliedFilters = memberPrivilegeFilters
    for
      snapshots <- IO.blocking(listSnapshots(context, requestedClubId, requestedPlayerId, query.privilege, rankCodeFilter))
    yield pagedResponse(snapshots, resolvedLimit, resolvedOffset, appliedFilters)

  private def memberPrivilegeFilters: Map[String, String] =
    Vector(
      query.playerId.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.PlayerId) -> _),
      query.privilege.map(value => QueryFilterField.toString(QueryFilterField.Privilege) -> ClubPrivilegeCode.toString(value)),
      query.rankCode.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.RankCode) -> _)
    ).flatten.toMap

  private def listSnapshots(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: Option[PlayerId],
      privilege: Option[ClubPrivilegeCode],
      rankCode: Option[String]
  ): Vector[ClubMemberPrivilegeSnapshot] =
    ClubTable.findById(context.connection, clubId)
      .map { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
        ClubFunctions.memberPrivilegeSnapshots(club)
      }
      .getOrElse(throw java.util.NoSuchElementException(s"Club ${clubId.value} was not found"))
      .filter(snapshot => playerId.forall(_ == snapshot.playerId))
      .filter(snapshot => privilege.forall(snapshot.privileges.contains))
      .filter(snapshot => rankCode.forall(_ == snapshot.rankCode.trim.toLowerCase))

  private def pagedResponse(
      snapshots: Vector[ClubMemberPrivilegeSnapshot],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  ): PagedResponse[ClubMemberPrivilegeSnapshotView] =
    require(limit > 0, "Input field limit must be positive")
    require(offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(limit, 100)
    val page = snapshots.slice(offset, offset + boundedLimit).map(ClubViewFunctions.memberPrivilegeSnapshotView)
    PagedResponse(
      items = page,
      total = snapshots.size,
      limit = boundedLimit,
      offset = offset,
      hasMore = offset + page.size < snapshots.size,
      appliedFilters = appliedFilters
    )
