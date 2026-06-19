package riichinexus.microservices.auth.domain
import riichinexus.microservices.auth.objects.Permission

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.auth.domain.model.AccessPrincipal

/** AuthorizationPolicy 表示后端领域中的授权策略状态或规则，包含canEvaluate、principal、权限、俱乐部 ID、赛事 ID、subjectPlayerId。 */

final case class AuthorizationPolicy(
    canEvaluate: AuthorizationPolicy.DecisionInput => Boolean
)

object AuthorizationPolicy:
  final case class DecisionInput(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  )
