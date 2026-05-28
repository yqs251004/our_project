package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.microservices.tournament.domain.model.{HandOutcome as DomainHandOutcome}
import upickle.default.*

enum TournamentPaifuHandOutcome derives CanEqual:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

  def toDomain: DomainHandOutcome =
    DomainHandOutcome.valueOf(toString)

object TournamentPaifuHandOutcome:
  given ReadWriter[TournamentPaifuHandOutcome] =
    readwriter[String].bimap(_.toString, TournamentPaifuHandOutcome.valueOf)

  def fromDomain(outcome: DomainHandOutcome): TournamentPaifuHandOutcome =
    TournamentPaifuHandOutcome.valueOf(outcome.toString)
