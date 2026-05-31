package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.model.GuestAccessSession
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
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
      sessions <- IO.blocking {
        guestSessions(context, query)
      }
    yield PagedResponse.fromItems(sessions, limit, offset, query.appliedFilters)(
      guestSessionResponse
    )

  private def guestSessions(
      context: ApiPlanContext,
      query: ResolvedGuestSessionsQuery
  ): Vector[GuestAccessSession] =
    GuestSessionTable
      .findAll(context.connection)
      .filter(session => query.activeOnly.forall(flag => !flag || session.canAuthenticate(query.asOf)))
      .sortBy(session => (session.createdAt, session.id.value))

  private def resolveQuery(asOf: Instant): ResolvedGuestSessionsQuery =
    ResolvedGuestSessionsQuery(
      activeOnly = activeOnly,
      asOf = asOf,
      appliedFilters = activeOnly.map(value => Map("activeOnly" -> value.toString)).getOrElse(Map.empty)
    )

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )

  private final case class ResolvedGuestSessionsQuery(
      activeOnly: Option[Boolean],
      asOf: Instant,
      appliedFilters: Map[String, String]
  )
