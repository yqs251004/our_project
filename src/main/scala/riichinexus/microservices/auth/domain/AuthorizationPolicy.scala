package riichinexus.microservices.auth.domain
import riichinexus.microservices.auth.objects.Permission

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.domain.model.AccessPrincipal

/** 可组合的授权判断策略。
  *
  * 策略接收一个完整的决策输入并返回是否允许访问，用于把主体、权限和资源上下文统一交给领域层判断。
  */
final case class AuthorizationPolicy(
    canEvaluate: AuthorizationPolicy.DecisionInput => Boolean
)

object AuthorizationPolicy:
  /** 单次授权判断所需的主体与资源上下文。 */
  final case class DecisionInput(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  )
