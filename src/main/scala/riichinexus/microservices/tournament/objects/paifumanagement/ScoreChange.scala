package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.domain.model.*

final case class ScoreChange(
    playerId: PlayerId,
    delta: Int
) derives CanEqual
