package riichinexus.api.http

import cats.effect.IO
import org.http4s.{Request, Response, Status}
import riichinexus.system.objects.PagedResponse
import upickle.default.*

object HttpPaginationSupport:

  def pagedJsonResponse[T: Writer](
      routeContext: RouteContext,
      request: Request[IO],
      items: Vector[T],
      appliedFilters: Map[String, String] = Map.empty,
      defaultLimit: Int = 20,
      maxLimit: Int = 100
  ): IO[Response[IO]] =
    val query = HttpRequestSupport.pageQuery(request, defaultLimit, maxLimit)
    val pagedItems = items.slice(query.offset, query.offset + query.limit)
    HttpResponseSupport.jsonResponse(
      routeContext,
      Status.Ok,
      PagedResponse(
        items = pagedItems,
        total = items.size,
        limit = query.limit,
        offset = query.offset,
        hasMore = query.offset + pagedItems.size < items.size,
        appliedFilters = appliedFilters
      )
    )
