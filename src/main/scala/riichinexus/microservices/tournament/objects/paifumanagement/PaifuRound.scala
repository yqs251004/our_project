package riichinexus.microservices.tournament.objects.paifumanagement

final case class PaifuRound(
    descriptor: KyokuDescriptor,
    players: Vector[PaifuRoundPlayer],
    timeline: PaifuTimeline,
    result: AgariResult
)
