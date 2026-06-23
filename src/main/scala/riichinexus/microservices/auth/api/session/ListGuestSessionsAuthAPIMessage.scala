package riichinexus.microservices.auth.api.session

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.session.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.session.model.GuestAccessSession
import riichinexus.microservices.auth.objects.session.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出游客访问会话。 */
final case class ListGuestSessionsAuthAPIMessage(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[GuestSessionResponse]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[GuestSessionResponse]] =
    for
      asOf <- IO.realTimeInstant
      appliedFilters = activeOnly
        .map(value => Map(QueryFilterField.toString(QueryFilterField.ActiveOnly) -> value.toString))
        .getOrElse(Map.empty)
      sessions <- IO.blocking {
        guestSessions(context, activeOnly, asOf)
      }
    yield PagedResponse.fromItems(sessions, limit, offset, appliedFilters)(
      guestSessionResponse
    )

  private def guestSessions(
      context: ApiPlanContext,
      activeOnly: Option[Boolean],
      asOf: Instant
  ): Vector[GuestAccessSession] =
    GuestSessionTable
      .findAll(context.connection)
      .filter(session => activeOnly.forall(flag => !flag || GuestAccessSessionFunctions.canAuthenticate(session, asOf)))
      .sortBy(session => (session.createdAt, session.id.value))

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )
