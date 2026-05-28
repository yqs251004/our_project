package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.{
  ClubContributionAuditEntry,
  ClubContributionAuditQuery
}
import riichinexus.microservices.club.tables.clubaudit.ClubContributionAuditTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListClubContributionAuditsAPIMessage(
    clubId: String,
    query: ClubContributionAuditQuery
) extends APIMessage[PagedResponse[ClubContributionAuditEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubContributionAuditEntry]] =
    for
      operator <- IO.blocking(context.principal(query.operatorId))
      _ <- IO.blocking(requireContributionAuditPermission(context, operator))
      parsedClubId = ClubId(clubId)
      resolved = resolveQuery(parsedClubId, query)
      audits <- IO.blocking(listContributionAudits(context, resolved))
    yield PagedResponse.fromItems(
      audits,
      query.limit,
      query.offset,
      resolved.appliedFilters
    )(ClubContributionAuditEntry.fromDomain(parsedClubId, _))

  private def requireContributionAuditPermission(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    context.support.requirePermission(operator, Permission.ViewAuditTrail)

  private def resolveQuery(
      clubId: ClubId,
      query: ClubContributionAuditQuery
  ): ResolvedContributionAuditQuery =
    ResolvedContributionAuditQuery(
      clubId = clubId,
      appliedFilters = Map(
        "clubId" -> clubId.value,
        "operatorId" -> query.operatorId.value
      )
    )

  private def listContributionAudits(
      context: ApiPlanContext,
      query: ResolvedContributionAuditQuery
  ): Vector[AuditEventEntry] =
    ClubContributionAuditTable.findContributionChanges(context.connection, query.clubId)

  private final case class ResolvedContributionAuditQuery(
      clubId: ClubId,
      appliedFilters: Map[String, String]
  )
