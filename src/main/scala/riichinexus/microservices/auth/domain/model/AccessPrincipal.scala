package riichinexus.microservices.auth.domain.model

import riichinexus.domain.model.PlayerId

final case class AccessPrincipal(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
) derives CanEqual
