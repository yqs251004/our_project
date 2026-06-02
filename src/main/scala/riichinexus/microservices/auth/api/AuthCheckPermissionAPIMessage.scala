package riichinexus.microservices.auth.api
import riichinexus.microservices.auth.objects.Permission

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.auth.utils.ResolveAccessPrincipal
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class AuthCheckPermissionAPIMessage(
    operatorId: Option[String] = None,
    principal: Option[AccessPrincipal] = None,
    permission: Permission,
    clubId: Option[String] = None,
    tournamentId: Option[String] = None,
    subjectPlayerId: Option[String] = None
) extends APIMessage[Boolean] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Boolean] =
    for
      input <- IO.blocking(resolveInput)
      operator <- resolvePrincipal(context, input.operatorId, input.principal)
      allowed <- IO.blocking(checkPermission(operator, input))
    yield allowed

  private def resolveInput: ResolvedCheckPermissionInput =
    ResolvedCheckPermissionInput(
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
      principal = principal,
      permission = permission,
      clubId = parseOptionalId(clubId)(ClubId(_)),
      tournamentId = parseOptionalId(tournamentId)(TournamentId(_)),
      subjectPlayerId = parseOptionalId(subjectPlayerId)(PlayerId(_))
    )

  private def parseOptionalId[A](value: Option[String])(parse: String => A): Option[A] =
    value.filter(_.nonEmpty).map(parse)

  private def resolvePrincipal(
      context: ApiPlanContext,
      operatorId: Option[PlayerId],
      principal: Option[AccessPrincipal]
  ): IO[AccessPrincipal] =
    principal match
      case Some(value) => IO.pure(value)
      case None =>
        operatorId match
          case Some(playerId) => ResolveAccessPrincipal(playerId).plan(context.connection)
          case None           => IO.raiseError(IllegalArgumentException("operatorId or principal is required"))

  private def checkPermission(operator: AccessPrincipal, input: ResolvedCheckPermissionInput): Boolean =
    AuthorizationPolicyFunctions.can(
      AuthorizationPolicyFunctions.strict,
      principal = operator,
      permission = input.permission,
      clubId = input.clubId,
      tournamentId = input.tournamentId,
      subjectPlayerId = input.subjectPlayerId
    )

  private final case class ResolvedCheckPermissionInput(
      operatorId: Option[PlayerId],
      principal: Option[AccessPrincipal],
      permission: Permission,
      clubId: Option[ClubId],
      tournamentId: Option[TournamentId],
      subjectPlayerId: Option[PlayerId]
  )
