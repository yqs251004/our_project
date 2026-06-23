package riichinexus.microservices.club.api.audit

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventPrivateView
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.audit.api.`private`.ListAuditEventsPrivateAPIMessage
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView

import riichinexus.microservices.club.objects.audit.apiTypes.{ClubContributionAuditQuery}
import riichinexus.microservices.club.objects.audit.{ClubContributionAuditEntry}
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出俱乐部贡献审计记录。 */
final case class ListClubContributionAuditsAPIMessage(
    clubId: String,
    query: ClubContributionAuditQuery
) extends APIMessage[PagedResponse[ClubContributionAuditEntry]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[ClubContributionAuditEntry]] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(query.operatorId).plan(context)
      _ <- requireContributionAuditPermission(context, operator)
      requestedClubId = ClubId(clubId)
      appliedFilters = contributionAuditFilters(requestedClubId, query)
      audits <- listContributionAudits(context, requestedClubId)
    yield PagedResponse.fromItems(
      audits,
      query.limit,
      query.offset,
      appliedFilters
    )(clubContributionAuditEntry(requestedClubId, _))

  private def requireContributionAuditPermission(context: ApiPlanContext, operator: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = operator.playerId.map(_.value),
      permission = Permission.ViewAuditTrail
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to view audit trail"))
    }

  private def contributionAuditFilters(
      clubId: ClubId,
      query: ClubContributionAuditQuery
  ): Map[String, String] =
    Map(
      QueryFilterField.toString(QueryFilterField.ClubId) -> clubId.value,
      QueryFilterField.toString(QueryFilterField.OperatorId) -> query.operatorId.value
    )

  private def listContributionAudits(
      context: ApiPlanContext,
      clubId: ClubId
  ): IO[Vector[AuditEventPrivateView]] =
    ListAuditEventsPrivateAPIMessage(
      aggregateType = Some(AggregateType.Club),
      aggregateId = Some(clubId.value),
      eventType = Some(AuditEventType.ClubMemberContributionAdjusted),
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
