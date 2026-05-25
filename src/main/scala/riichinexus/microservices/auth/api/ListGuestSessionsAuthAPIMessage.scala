package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListGuestSessionsAuthAPIMessage(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[GuestSessionResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[GuestSessionResponse]] =
    for
      asOf <- IO.realTimeInstant
      query = resolveQuery(asOf)
      sessions <- IO {
        context.support.authModule.guestSessionTable.list(
          activeOnly = query.activeOnly,
          asOf = query.asOf
        )
      }
    yield PagedResponse.fromItems(sessions, limit, offset, query.appliedFilters)(
      GuestSessionResponse.fromDomain
    )

  private def resolveQuery(asOf: Instant): ResolvedGuestSessionsQuery =
    ResolvedGuestSessionsQuery(
      activeOnly = activeOnly,
      asOf = asOf,
      appliedFilters = activeOnly.map(value => Map("activeOnly" -> value.toString)).getOrElse(Map.empty)
    )

  private final case class ResolvedGuestSessionsQuery(
      activeOnly: Option[Boolean],
      asOf: Instant,
      appliedFilters: Map[String, String]
  )
