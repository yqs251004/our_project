package riichinexus.microservices.auth.objects

import java.time.Instant

import riichinexus.domain.model.{GuestSessionId, PlayerId}

final case class CurrentSessionQuery(
    operatorId: Option[PlayerId] = None,
    guestSessionId: Option[GuestSessionId] = None
)

final case class GuestSessionListQuery(
    activeOnly: Option[Boolean] = None,
    asOf: Instant = Instant.now()
)
