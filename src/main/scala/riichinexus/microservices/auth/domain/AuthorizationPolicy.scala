package riichinexus.microservices.auth.domain

import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.AccessPrincipal

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
