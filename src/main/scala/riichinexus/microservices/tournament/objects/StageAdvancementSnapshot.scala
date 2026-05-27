package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{StageAdvancementSnapshot as DomainStageAdvancementSnapshot}
import upickle.default.*

final case class StageAdvancementSnapshot(
    tournamentId: String,
    stageId: String,
    generatedAt: String,
    rule: String,
    standings: Vector[StageStandingEntry],
    qualifiedPlayerIds: Vector[String],
    reservePlayerIds: Vector[String],
    summary: String
) derives ReadWriter

object StageAdvancementSnapshot:
  def fromDomain(snapshot: DomainStageAdvancementSnapshot): StageAdvancementSnapshot =
    StageAdvancementSnapshot(
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      generatedAt = snapshot.generatedAt.toString,
      rule = snapshot.rule.ruleType.toString,
      standings = snapshot.standings.map(StageStandingEntry.fromDomain),
      qualifiedPlayerIds = snapshot.qualifiedPlayerIds.map(_.value),
      reservePlayerIds = snapshot.reservePlayerIds.map(_.value),
      summary = snapshot.summary
    )
