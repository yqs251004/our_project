package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.{
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
      operator <- IO.blocking(AuthAccessPrincipalResolver.principal(context, query.operatorId))
      _ <- IO.blocking(requireContributionAuditPermission(context, operator))
      parsedClubId = ClubId(clubId)
      resolved = resolveQuery(parsedClubId, query)
      audits <- IO.blocking(listContributionAudits(context, resolved))
    yield PagedResponse.fromItems(
      audits,
      query.limit,
      query.offset,
      resolved.appliedFilters
    )(clubContributionAuditEntry(parsedClubId, _))

  private def requireContributionAuditPermission(context: ApiPlanContext, operator: AccessPrincipal): Unit =
    riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions.requirePermission(context.support.authorizationService, operator, Permission.ViewAuditTrail)

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

  private def clubContributionAuditEntry(clubId: ClubId, entry: AuditEventEntry): ClubContributionAuditEntry =
    ClubContributionAuditEntry(
      id = entry.id.value,
      clubId = clubId.value,
      playerId = entry.details.get("playerId"),
      delta = entry.details.get("delta"),
      contribution = entry.details.get("contribution"),
      occurredAt = entry.occurredAt.toString,
      actorId = entry.actorId.map(_.value),
      note = entry.note
    )

  private final case class ResolvedContributionAuditQuery(
      clubId: ClubId,
      appliedFilters: Map[String, String]
  )
