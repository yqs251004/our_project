package riichinexus.microservices.club.api.profile
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.domain.profile.model.Club

import riichinexus.microservices.club.objects.profile.ClubView
import riichinexus.microservices.club.objects.profile.apiTypes.ClubListQuery
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出管理视角的俱乐部。 */
final case class ListClubsAPIMessage(
    query: ClubListQuery = ClubListQuery()
) extends APIMessage[PagedResponse[ClubView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubView]] =
    val activeOnly = query.activeOnly.contains(true)
    val joinableOnly = query.joinableOnly.contains(true)
    val memberId = query.memberId.filter(_.nonEmpty).map(PlayerId(_))
    val adminId = query.adminId.filter(_.nonEmpty).map(PlayerId(_))
    val name = query.name.filter(_.nonEmpty)
    val resolvedLimit = query.limit.getOrElse(20)
    val resolvedOffset = query.offset.getOrElse(0)
    val appliedFilters = clubListFilters
    for
      clubs <- IO.blocking(listClubs(context, activeOnly, joinableOnly, memberId, adminId, name))
    yield pagedResponse(clubs, resolvedLimit, resolvedOffset, appliedFilters)

  private def clubListFilters: Map[String, String] =
    Vector(
      query.activeOnly.map(value => QueryFilterField.toString(QueryFilterField.ActiveOnly) -> value.toString),
      query.joinableOnly.map(value => QueryFilterField.toString(QueryFilterField.JoinableOnly) -> value.toString),
      query.memberId.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.MemberId) -> _),
      query.adminId.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.AdminId) -> _),
      query.name.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.Name) -> _)
    ).flatten.toMap

  private def listClubs(
      context: ApiPlanContext,
      activeOnly: Boolean,
      joinableOnly: Boolean,
      memberId: Option[PlayerId],
      adminId: Option[PlayerId],
      name: Option[String]
  ): Vector[Club] =
    ClubTable
      .findFiltered(
        context.connection,
        activeOnly = activeOnly,
        joinableOnly = joinableOnly,
        memberId = memberId,
        adminId = adminId,
        name = name
      )
      .sortBy(club => (club.dissolvedAt.nonEmpty, club.name, club.id.value))

  private def pagedResponse(
      clubs: Vector[Club],
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  ): PagedResponse[ClubView] =
    require(limit > 0, "Input field limit must be positive")
    require(offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(limit, 100)
    val page = clubs.slice(offset, offset + boundedLimit).map(ClubViewFunctions.clubView)
    PagedResponse(
      items = page,
      total = clubs.size,
      limit = boundedLimit,
      offset = offset,
      hasMore = offset + page.size < clubs.size,
      appliedFilters = appliedFilters
    )
