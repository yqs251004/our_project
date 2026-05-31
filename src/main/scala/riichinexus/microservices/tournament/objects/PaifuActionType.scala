package riichinexus.microservices.tournament.objects

import upickle.default.*

enum PaifuActionType derives CanEqual:
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

object PaifuActionType:
  given ReadWriter[PaifuActionType] =
    readwriter[String].bimap(_.toString, PaifuActionType.valueOf)
