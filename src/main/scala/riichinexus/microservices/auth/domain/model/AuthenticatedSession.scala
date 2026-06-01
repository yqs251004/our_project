package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.domain.model.PlayerId

final case class AuthenticatedSession(
    token: String,
    username: String,
    playerId: PlayerId,
    createdAt: Instant,
    expiresAt: Instant,
    lastSeenAt: Option[Instant] = None,
    revokedAt: Option[Instant] = None,
    version: Int = 0
) derives CanEqual
