package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.microservices.tournament.domain.model.{PaifuActionType as DomainPaifuActionType}
import upickle.default.*

enum TournamentPaifuActionType derives CanEqual:
  case Draw
  case Discard
  case Chi
  case Pon
  case Kan
  case Riichi
  case DoraReveal
  case Win
  case DrawGame
  case AddedKan
  case ClosedKan
  case OpenKan

  def toDomain: DomainPaifuActionType =
    DomainPaifuActionType.valueOf(toString)

object TournamentPaifuActionType:
  given ReadWriter[TournamentPaifuActionType] =
    readwriter[String].bimap(_.toString, TournamentPaifuActionType.valueOf)

  def fromDomain(actionType: DomainPaifuActionType): TournamentPaifuActionType =
    TournamentPaifuActionType.valueOf(actionType.toString)
