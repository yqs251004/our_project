package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.ClubMemberPrivilegeSnapshot

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubMemberPrivilegeListQuery
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.objects.PagedResponse
import upickle.default.ReadWriter

/** 列出俱乐部成员权限。 */
final case class ListClubMemberPrivilegesAPIMessage(
    clubId: String,
    query: ClubMemberPrivilegeListQuery = ClubMemberPrivilegeListQuery()
) extends APIMessage[PagedResponse[ClubMemberPrivilegeSnapshotView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubMemberPrivilegeSnapshotView]] =
    for
      resolved <- IO.pure(resolveQuery)
      snapshots <- IO.blocking(listSnapshots(context, resolved))
    yield pagedResponse(snapshots, resolved)

  private def resolveQuery: ResolvedClubMemberPrivilegeQuery =
    ResolvedClubMemberPrivilegeQuery(
      clubId = ClubId(clubId),
      playerId = query.playerId.filter(_.nonEmpty).map(PlayerId(_)),
      privilege = query.privilege,
      rankCode = query.rankCode.filter(_.nonEmpty).map(_.trim.toLowerCase),
      limit = query.limit.getOrElse(20),
      offset = query.offset.getOrElse(0),
      appliedFilters = Vector(
        query.playerId.filter(_.nonEmpty).map("playerId" -> _),
        query.privilege.map(value => "privilege" -> ClubPrivilegeCode.toString(value)),
        query.rankCode.filter(_.nonEmpty).map("rankCode" -> _)
      ).flatten.toMap
    )

  private def listSnapshots(
      context: ApiPlanContext,
      query: ResolvedClubMemberPrivilegeQuery
  ): Vector[ClubMemberPrivilegeSnapshot] =
    ClubTable.findById(context.connection, query.clubId)
      .map { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
        ClubFunctions.memberPrivilegeSnapshots(club)
      }
      .getOrElse(throw java.util.NoSuchElementException(s"Club ${query.clubId.value} was not found"))
      .filter(snapshot => query.playerId.forall(_ == snapshot.playerId))
      .filter(snapshot => query.privilege.forall(snapshot.privileges.contains))
      .filter(snapshot => query.rankCode.forall(_ == snapshot.rankCode.trim.toLowerCase))

  private def pagedResponse(
      snapshots: Vector[ClubMemberPrivilegeSnapshot],
      query: ResolvedClubMemberPrivilegeQuery
  ): PagedResponse[ClubMemberPrivilegeSnapshotView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val page = snapshots.slice(query.offset, query.offset + boundedLimit).map(ClubMemberPrivilegeSnapshotView.fromDomain)
    PagedResponse(
      items = page,
      total = snapshots.size,
      limit = boundedLimit,
      offset = query.offset,
      hasMore = query.offset + page.size < snapshots.size,
      appliedFilters = query.appliedFilters
    )

  private final case class ResolvedClubMemberPrivilegeQuery(
      clubId: ClubId,
      playerId: Option[PlayerId],
      privilege: Option[ClubPrivilegeCode],
      rankCode: Option[String],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
