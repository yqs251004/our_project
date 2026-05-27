package riichinexus.microservices.opsanalytics.domain

import riichinexus.domain.model.PlayerId

final case class RatingChange(
    playerId: PlayerId,
    delta: Int
) derives CanEqual
