package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.GuestAccessSession
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListGuestSessionsAuthAPIMessage(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[GuestSessionResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[GuestAccessSession]] =
    IO {
      val boundedLimit = math.min(limit.getOrElse(20), 100)
      val pageOffset = offset.getOrElse(0)
      require(boundedLimit > 0, "Input field limit must be positive")
      require(pageOffset >= 0, "Input field offset must be non-negative")

      val sessions = context.support.authModule.guestSessionTable.list(
        activeOnly = activeOnly,
        asOf = Instant.now()
      )
      val page = sessions.slice(pageOffset, pageOffset + boundedLimit)
      PagedResponse(
        items = page,
        total = sessions.size,
        limit = boundedLimit,
        offset = pageOffset,
        hasMore = pageOffset + page.size < sessions.size,
        appliedFilters = activeOnly.map(value => Map("activeOnly" -> value.toString)).getOrElse(Map.empty)
      )
    }
