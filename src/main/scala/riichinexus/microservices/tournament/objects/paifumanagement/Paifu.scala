package riichinexus.microservices.tournament.objects.paifumanagement

import riichinexus.domain.model.*

final case class Paifu(
    id: PaifuId,
    metadata: PaifuMetadata,
    rounds: Vector[PaifuRound],
    finalStandings: Vector[FinalStanding]
) derives CanEqual
