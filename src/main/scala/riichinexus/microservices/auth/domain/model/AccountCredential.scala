package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.domain.model.PlayerId

final case class AccountCredential(
    username: String,
    playerId: PlayerId,
    passwordHash: String,
    passwordSalt: String,
    passwordIterations: Int,
    createdAt: Instant,
    updatedAt: Instant,
    version: Int = 0
) derives CanEqual
