package riichinexus.microservices.auth.api
import riichinexus.microservices.auth.objects.Permission

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.auth.api.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.domain.functions.AccessPrincipalPrivateViewFunctions
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.system.json.JsonCodecs.given
/** 检查访问主体是否拥有指定权限。 */
final case class AuthCheckPermissionAPIMessage(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None,
    permission: Permission,
    clubId: Option[String] = None,
    tournamentId: Option[String] = None,
    subjectPlayerId: Option[String] = None
) extends APIMessage[Boolean]:

  override def plan(context: ApiPlanContext): IO[Boolean] =
    for
      input <- IO.blocking(resolveInput)
      operator <- resolvePrincipal(context, input.guestSessionId, input.operatorId)
      allowed <- IO.blocking(checkPermission(operator, input))
    yield allowed

  private def resolveInput: ResolvedCheckPermissionInput =
    ResolvedCheckPermissionInput(
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
      guestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)),
      permission = permission,
      clubId = parseOptionalId(clubId)(ClubId(_)),
      tournamentId = parseOptionalId(tournamentId)(TournamentId(_)),
      subjectPlayerId = parseOptionalId(subjectPlayerId)(PlayerId(_))
    )

  private def parseOptionalId[A](value: Option[String])(parse: String => A): Option[A] =
    value.filter(_.nonEmpty).map(parse)

  private def resolvePrincipal(
      context: ApiPlanContext,
      guestSessionId: Option[GuestSessionId],
      operatorId: Option[PlayerId]
  ): IO[AccessPrincipalPrivateView] =
    ResolveRequestActorPrivateAPIMessage(guestSessionId, operatorId).plan(context)

  private def checkPermission(operator: AccessPrincipalPrivateView, input: ResolvedCheckPermissionInput): Boolean =
    AuthorizationPolicyFunctions.can(
      AuthorizationPolicyFunctions.strict,
      principal = AccessPrincipalPrivateViewFunctions.toDomain(operator),
      permission = input.permission,
      clubId = input.clubId,
      tournamentId = input.tournamentId,
      subjectPlayerId = input.subjectPlayerId
    )

  /** 权限检查接口解析后的主体与资源上下文。 */
  private final case class ResolvedCheckPermissionInput(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId],
      permission: Permission,
      clubId: Option[ClubId],
      tournamentId: Option[TournamentId],
      subjectPlayerId: Option[PlayerId]
  )
