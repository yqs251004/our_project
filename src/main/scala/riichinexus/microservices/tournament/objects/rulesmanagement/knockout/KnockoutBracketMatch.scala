package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class KnockoutBracketMatch(
    id: String,
    roundNumber: Int,
    position: Int,
    lane: KnockoutLane = KnockoutLane.Championship,
    slots: Vector[KnockoutBracketSlot],
    sourceMatchIds: Vector[String] = Vector.empty,
    advancementCount: Int,
    nextMatchId: Option[String] = None,
    tableId: Option[TableId] = None,
    unlocked: Boolean = false,
    completed: Boolean = false,
    results: Vector[KnockoutBracketResult] = Vector.empty
) derives CanEqual, ReadWriter
