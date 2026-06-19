package riichinexus.microservices.club.api
import riichinexus.microservices.audit.objects.`private`.AuditEventPrivateView
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.api.`private`.ListAuditEventsPrivateAPIMessage
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

import riichinexus.microservices.club.objects.auditreadmodel.apiTypes.{ClubContributionAuditEntry, ClubContributionAuditQuery}
import riichinexus.system.objects.PagedResponse
import upickle.default.ReadWriter

/** 列出俱乐部贡献审计记录。 */
final case class ListClubContributionAuditsAPIMessage(
    clubId: String,
    query: ClubContributionAuditQuery
) extends APIMessage[PagedResponse[ClubContributionAuditEntry]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubContributionAuditEntry]] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(query.operatorId).plan(context)
      _ <- requireContributionAuditPermission(context, operator)
      parsedClubId = ClubId(clubId)
      resolved = resolveQuery(parsedClubId, query)
      audits <- listContributionAudits(context, resolved)
    yield PagedResponse.fromItems(
      audits,
      query.limit,
      query.offset,
      resolved.appliedFilters
    )(clubContributionAuditEntry(parsedClubId, _))

  private def requireContributionAuditPermission(context: ApiPlanContext, operator: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = operator.playerId.map(_.value),
      permission = Permission.ViewAuditTrail
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to view audit trail"))
    }

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
  ): IO[Vector[AuditEventPrivateView]] =
    ListAuditEventsPrivateAPIMessage(
      aggregateType = Some("club"),
      aggregateId = Some(query.clubId.value),
      eventType = Some("ClubMemberContributionAdjusted"),
      oldestFirst = true
    ).plan(context)

  private def clubContributionAuditEntry(clubId: ClubId, entry: AuditEventPrivateView): ClubContributionAuditEntry =
    ClubContributionAuditEntry(
      id = entry.id,
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
