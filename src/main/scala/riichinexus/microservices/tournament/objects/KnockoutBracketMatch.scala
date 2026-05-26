package riichinexus.microservices.tournament.objects

import riichinexus.domain.model.{KnockoutBracketMatch as DomainKnockoutBracketMatch}
import upickle.default.*

final case class KnockoutBracketMatch(
    id: String,
    roundNumber: Int,
    position: Int,
    lane: String,
    slots: Vector[KnockoutBracketSlot],
    sourceMatchIds: Vector[String],
    advancementCount: Int,
    nextMatchId: Option[String],
    tableId: Option[String],
    unlocked: Boolean,
    completed: Boolean,
    results: Vector[KnockoutBracketResult]
) derives ReadWriter

object KnockoutBracketMatch:
  def fromDomain(matchView: DomainKnockoutBracketMatch): KnockoutBracketMatch =
    KnockoutBracketMatch(
      id = matchView.id,
      roundNumber = matchView.roundNumber,
      position = matchView.position,
      lane = matchView.lane.toString,
      slots = matchView.slots.map(KnockoutBracketSlot.fromDomain),
      sourceMatchIds = matchView.sourceMatchIds,
      advancementCount = matchView.advancementCount,
      nextMatchId = matchView.nextMatchId,
      tableId = matchView.tableId.map(_.value),
      unlocked = matchView.unlocked,
      completed = matchView.completed,
      results = matchView.results.map(KnockoutBracketResult.fromDomain)
    )
