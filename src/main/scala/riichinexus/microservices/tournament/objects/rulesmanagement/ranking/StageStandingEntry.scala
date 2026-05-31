package riichinexus.microservices.tournament.objects.rulesmanagement.ranking

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class StageStandingEntry(
    playerId: PlayerId,
    matchesPlayed: Int,
    placementPoints: Int,
    totalScoreDelta: Int,
    totalFinalPoints: Int,
    averagePlacement: Double,
    qualified: Boolean = false,
    seed: Option[Int] = None
) derives CanEqual, ReadWriter
