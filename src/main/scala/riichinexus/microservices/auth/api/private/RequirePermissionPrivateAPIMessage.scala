package riichinexus.microservices.auth.api.`private`

import cats.effect.IO
import riichinexus.microservices.auth.domain.functions.AccessPrincipalPrivateViewFunctions
import riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 供后端服务强制执行权限校验。 */
final case class RequirePermissionPrivateAPIMessage(
    principal: AccessPrincipalPrivateView,
    permission: Permission,
    clubId: Option[ClubId] = None,
    tournamentId: Option[TournamentId] = None,
    subjectPlayerId: Option[PlayerId] = None
) extends APIMessage[Unit]:

  override def plan(context: ApiPlanContext): IO[Unit] =
    IO.blocking {
      AuthorizationPolicyFunctions.requirePermission(
        AuthorizationPolicyFunctions.strict,
        principal = AccessPrincipalPrivateViewFunctions.toDomain(principal),
        permission = permission,
        clubId = clubId,
        tournamentId = tournamentId,
        subjectPlayerId = subjectPlayerId
      )
    }
