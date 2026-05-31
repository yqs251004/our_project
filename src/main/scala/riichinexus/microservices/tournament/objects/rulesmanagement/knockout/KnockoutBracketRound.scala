package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import upickle.default.*

final case class KnockoutBracketRound(
    roundNumber: Int,
    label: String,
    matches: Vector[KnockoutBracketMatch]
) derives CanEqual, ReadWriter
