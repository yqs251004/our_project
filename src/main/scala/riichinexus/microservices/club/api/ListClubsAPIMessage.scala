package riichinexus.microservices.club.api

import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.Club

import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.club.objects.clubmanagement.apiTypes.ClubListQuery
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.objects.PagedResponse
/** 列出管理视角的俱乐部。 */
final case class ListClubsAPIMessage(
    query: ClubListQuery = ClubListQuery()
) extends APIMessage[PagedResponse[ClubView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubView]] =
    for
      resolved <- IO.pure(resolveQuery)
      clubs <- IO.blocking(listClubs(context, resolved))
    yield pagedResponse(clubs, resolved)

  private def resolveQuery: ResolvedClubListQuery =
    ResolvedClubListQuery(
      activeOnly = query.activeOnly.contains(true),
      joinableOnly = query.joinableOnly.contains(true),
      memberId = query.memberId.filter(_.nonEmpty).map(PlayerId(_)),
      adminId = query.adminId.filter(_.nonEmpty).map(PlayerId(_)),
      name = query.name.filter(_.nonEmpty),
      limit = query.limit.getOrElse(20),
      offset = query.offset.getOrElse(0),
      appliedFilters = Vector(
        query.activeOnly.map(value => "activeOnly" -> value.toString),
        query.joinableOnly.map(value => "joinableOnly" -> value.toString),
        query.memberId.filter(_.nonEmpty).map("memberId" -> _),
        query.adminId.filter(_.nonEmpty).map("adminId" -> _),
        query.name.filter(_.nonEmpty).map("name" -> _)
      ).flatten.toMap
    )

  private def listClubs(
      context: ApiPlanContext,
      query: ResolvedClubListQuery
  ): Vector[Club] =
    ClubTable
      .findFiltered(
        context.connection,
        activeOnly = query.activeOnly,
        joinableOnly = query.joinableOnly,
        memberId = query.memberId,
        adminId = query.adminId,
        name = query.name
      )
      .sortBy(club => (club.dissolvedAt.nonEmpty, club.name, club.id.value))

  private def pagedResponse(
      clubs: Vector[Club],
      query: ResolvedClubListQuery
  ): PagedResponse[ClubView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val page = clubs.slice(query.offset, query.offset + boundedLimit).map(ClubViewFunctions.clubView)
    PagedResponse(
      items = page,
      total = clubs.size,
      limit = boundedLimit,
      offset = query.offset,
      hasMore = query.offset + page.size < clubs.size,
      appliedFilters = query.appliedFilters
    )

  /** 管理侧俱乐部列表接口解析后的过滤与分页条件。 */
  private final case class ResolvedClubListQuery(
      activeOnly: Boolean,
      joinableOnly: Boolean,
      memberId: Option[PlayerId],
      adminId: Option[PlayerId],
      name: Option[String],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
