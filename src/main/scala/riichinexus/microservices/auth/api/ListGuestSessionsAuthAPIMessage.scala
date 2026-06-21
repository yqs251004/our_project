package riichinexus.microservices.auth.api

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.model.GuestAccessSession
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import riichinexus.system.objects.PagedResponse
/** 列出游客访问会话。 */
final case class ListGuestSessionsAuthAPIMessage(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[GuestSessionResponse]]:

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
      .filter(session => query.activeOnly.forall(flag => !flag || GuestAccessSessionFunctions.canAuthenticate(session, query.asOf)))
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

  /** 游客会话列表接口解析后的过滤条件和时间基准。 */
  private final case class ResolvedGuestSessionsQuery(
      activeOnly: Option[Boolean],
      asOf: Instant,
      appliedFilters: Map[String, String]
  )
