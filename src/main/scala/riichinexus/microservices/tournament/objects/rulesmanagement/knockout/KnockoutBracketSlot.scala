package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class KnockoutBracketSlot(
    seed: Int,
    playerId: Option[PlayerId],
    bye: Boolean = false,
    sourceMatchId: Option[String] = None,
    sourcePlacement: Option[Int] = None
) derives CanEqual, ReadWriter
