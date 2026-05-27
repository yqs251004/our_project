package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{KnockoutBracketRound as DomainKnockoutBracketRound}
import upickle.default.*

final case class KnockoutBracketRound(
    roundNumber: Int,
    label: String,
    matches: Vector[KnockoutBracketMatch]
) derives ReadWriter

object KnockoutBracketRound:
  def fromDomain(round: DomainKnockoutBracketRound): KnockoutBracketRound =
    KnockoutBracketRound(
      roundNumber = round.roundNumber,
      label = round.label,
      matches = round.matches.map(KnockoutBracketMatch.fromDomain)
    )
