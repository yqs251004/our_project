package riichinexus.microservices.auth.api.authorization
import riichinexus.microservices.auth.objects.authorization.Permission

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.domain.authorization.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.auth.api.authorization.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.domain.authorization.functions.AccessPrincipalPrivateViewFunctions
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.session.GuestSessionId
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
      resolvedOperatorId <- IO.blocking(operatorId.filter(_.nonEmpty).map(PlayerId(_)))
      resolvedGuestSessionId <- IO.blocking(guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)))
      resolvedClubId <- IO.blocking(parseOptionalId(clubId)(ClubId(_)))
      resolvedTournamentId <- IO.blocking(parseOptionalId(tournamentId)(TournamentId(_)))
      resolvedSubjectPlayerId <- IO.blocking(parseOptionalId(subjectPlayerId)(PlayerId(_)))
      operator <- resolvePrincipal(context, resolvedGuestSessionId, resolvedOperatorId)
      allowed <- IO.blocking(checkPermission(operator, resolvedClubId, resolvedTournamentId, resolvedSubjectPlayerId))
    yield allowed

  private def parseOptionalId[A](value: Option[String])(parse: String => A): Option[A] =
    value.filter(_.nonEmpty).map(parse)

  private def resolvePrincipal(
      context: ApiPlanContext,
      guestSessionId: Option[GuestSessionId],
      operatorId: Option[PlayerId]
  ): IO[AccessPrincipalPrivateView] =
    ResolveRequestActorPrivateAPIMessage(guestSessionId, operatorId).plan(context)

  private def checkPermission(
      operator: AccessPrincipalPrivateView,
      clubId: Option[ClubId],
      tournamentId: Option[TournamentId],
      subjectPlayerId: Option[PlayerId]
  ): Boolean =
    AuthorizationPolicyFunctions.can(
      AuthorizationPolicyFunctions.strict,
      principal = AccessPrincipalPrivateViewFunctions.toDomain(operator),
      permission = permission,
      clubId = clubId,
      tournamentId = tournamentId,
      subjectPlayerId = subjectPlayerId
    )
