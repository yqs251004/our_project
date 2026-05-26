package riichinexus.microservices.tournament.objects

import riichinexus.domain.model.{KnockoutBracketSlot as DomainKnockoutBracketSlot}
import upickle.default.*

final case class KnockoutBracketSlot(
    seed: Int,
    playerId: Option[String],
    bye: Boolean,
    sourceMatchId: Option[String],
    sourcePlacement: Option[Int]
) derives ReadWriter

object KnockoutBracketSlot:
  def fromDomain(slot: DomainKnockoutBracketSlot): KnockoutBracketSlot =
    KnockoutBracketSlot(
      seed = slot.seed,
      playerId = slot.playerId.map(_.value),
      bye = slot.bye,
      sourceMatchId = slot.sourceMatchId,
      sourcePlacement = slot.sourcePlacement
    )
