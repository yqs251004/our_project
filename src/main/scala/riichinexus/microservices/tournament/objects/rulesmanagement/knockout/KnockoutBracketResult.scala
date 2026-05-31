package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class KnockoutBracketResult(
    playerId: PlayerId,
    placement: Int,
    finalPoints: Int,
    advanced: Boolean
) derives CanEqual, ReadWriter
