package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentSettlementEntryView(
    playerId: String,
    rank: Int,
    awardAmount: Long,
    baseAwardAmount: Long,
    adjustmentAmount: Long,
    deductionAmount: Long,
    clubId: Option[String],
    clubShareAmount: Long,
    playerRetainedAmount: Long,
    finalPoints: Int,
    champion: Boolean
) derives CanEqual

object TournamentSettlementEntryView:
  def fromDomain(entry: TournamentSettlementEntry): TournamentSettlementEntryView =
    TournamentSettlementEntryView(
      playerId = entry.playerId.value,
      rank = entry.rank,
      awardAmount = entry.awardAmount,
      baseAwardAmount = entry.baseAwardAmount,
      adjustmentAmount = entry.adjustmentAmount,
      deductionAmount = entry.deductionAmount,
      clubId = entry.clubId.map(_.value),
      clubShareAmount = entry.clubShareAmount,
      playerRetainedAmount = entry.playerRetainedAmount,
      finalPoints = entry.finalPoints,
      champion = entry.champion
    )

  given ReadWriter[TournamentSettlementEntryView] = macroRW

