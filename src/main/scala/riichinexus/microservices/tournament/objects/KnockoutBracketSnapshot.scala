package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{KnockoutBracketSnapshot as DomainKnockoutBracketSnapshot}
import upickle.default.*

final case class KnockoutBracketSnapshot(
    tournamentId: String,
    stageId: String,
    generatedAt: String,
    bracketSize: Int,
    qualifiedPlayerIds: Vector[String],
    rounds: Vector[KnockoutBracketRound],
    summary: String
) derives ReadWriter

object KnockoutBracketSnapshot:
  def fromDomain(snapshot: DomainKnockoutBracketSnapshot): KnockoutBracketSnapshot =
    KnockoutBracketSnapshot(
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      generatedAt = snapshot.generatedAt.toString,
      bracketSize = snapshot.bracketSize,
      qualifiedPlayerIds = snapshot.qualifiedPlayerIds.map(_.value),
      rounds = snapshot.rounds.map(KnockoutBracketRound.fromDomain),
      summary = snapshot.summary
    )
