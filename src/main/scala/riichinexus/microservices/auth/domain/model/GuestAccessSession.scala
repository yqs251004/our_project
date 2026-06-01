package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.domain.model.{GuestSessionId, PlayerId}

final case class GuestAccessSession(
    id: GuestSessionId,
    createdAt: Instant,
    displayName: String = "guest",
    expiresAt: Instant,
    lastSeenAt: Option[Instant] = None,
    revokedAt: Option[Instant] = None,
    revokedReason: Option[String] = None,
    deviceFingerprint: Option[String] = None,
    upgradedToPlayerId: Option[PlayerId] = None,
    version: Int = 0
) derives CanEqual
