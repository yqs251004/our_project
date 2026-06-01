package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId, TournamentId}

final case class RoleGrant(
    role: Role,
    grantedAt: Instant,
    grantedBy: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    tournamentId: Option[TournamentId] = None
) derives CanEqual
