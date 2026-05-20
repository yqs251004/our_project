package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.system.objects.apiTypes.PagedResponse
import upickle.default.*

final case class ListClubsAPIMessage(
    activeOnly: Option[Boolean] = None,
    joinableOnly: Option[Boolean] = None,
    memberId: Option[String] = None,
    adminId: Option[String] = None,
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[Club]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[Club]] =
    IO {
      val parsedActiveOnly = activeOnly.contains(true)
      val parsedJoinableOnly = joinableOnly.contains(true)
      val parsedMemberId = memberId.filter(_.nonEmpty).map(PlayerId(_))
      val parsedAdminId = adminId.filter(_.nonEmpty).map(PlayerId(_))
      val parsedName = name.filter(_.nonEmpty)
      val clubs = context.support.clubModule.tables
        .listClubs(
          activeOnly = parsedActiveOnly,
          joinableOnly = parsedJoinableOnly,
          memberId = parsedMemberId,
          adminId = parsedAdminId,
          name = parsedName
        )
        .sortBy(club => (club.dissolvedAt.nonEmpty, club.name, club.id.value))
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = clubs.slice(resolvedOffset, resolvedOffset + boundedLimit)
      PagedResponse(
        items = page,
        total = clubs.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < clubs.size,
        appliedFilters = Vector(
          activeOnly.map(value => "activeOnly" -> value.toString),
          joinableOnly.map(value => "joinableOnly" -> value.toString),
          memberId.filter(_.nonEmpty).map("memberId" -> _),
          adminId.filter(_.nonEmpty).map("adminId" -> _),
          name.filter(_.nonEmpty).map("name" -> _)
        ).flatten.toMap
      )
    }
