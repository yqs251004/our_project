package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{StageStandingEntry as DomainStageStandingEntry}
import upickle.default.*

final case class StageStandingEntry(
    playerId: String,
    matchesPlayed: Int,
    placementPoints: Int,
    totalScoreDelta: Int,
    totalFinalPoints: Int,
    averagePlacement: Double,
    qualified: Boolean,
    seed: Option[Int]
) derives ReadWriter

object StageStandingEntry:
  def fromDomain(entry: DomainStageStandingEntry): StageStandingEntry =
    StageStandingEntry(
      playerId = entry.playerId.value,
      matchesPlayed = entry.matchesPlayed,
      placementPoints = entry.placementPoints,
      totalScoreDelta = entry.totalScoreDelta,
      totalFinalPoints = entry.totalFinalPoints,
      averagePlacement = entry.averagePlacement,
      qualified = entry.qualified,
      seed = entry.seed
    )
