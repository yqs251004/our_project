package riichinexus.microservices.tournament.objects

import riichinexus.domain.model.{StageRankingSnapshot as DomainStageRankingSnapshot}
import upickle.default.*

final case class StageRankingSnapshot(
    tournamentId: String,
    stageId: String,
    generatedAt: String,
    entries: Vector[StageStandingEntry],
    archivedTableCount: Int,
    scheduledTableCount: Int
) derives ReadWriter

object StageRankingSnapshot:
  def fromDomain(snapshot: DomainStageRankingSnapshot): StageRankingSnapshot =
    StageRankingSnapshot(
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      generatedAt = snapshot.generatedAt.toString,
      entries = snapshot.entries.map(StageStandingEntry.fromDomain),
      archivedTableCount = snapshot.archivedTableCount,
      scheduledTableCount = snapshot.scheduledTableCount
    )
