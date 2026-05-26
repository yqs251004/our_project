package riichinexus.microservices.tournament.objects

import riichinexus.domain.model.{KnockoutBracketResult as DomainKnockoutBracketResult}
import upickle.default.*

final case class KnockoutBracketResult(
    playerId: String,
    placement: Int,
    finalPoints: Int,
    advanced: Boolean
) derives ReadWriter

object KnockoutBracketResult:
  def fromDomain(result: DomainKnockoutBracketResult): KnockoutBracketResult =
    KnockoutBracketResult(
      playerId = result.playerId.value,
      placement = result.placement,
      finalPoints = result.finalPoints,
      advanced = result.advanced
    )
