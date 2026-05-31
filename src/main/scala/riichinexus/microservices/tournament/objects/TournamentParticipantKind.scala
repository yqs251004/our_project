package riichinexus.microservices.tournament.objects

import upickle.default.*

enum TournamentParticipantKind derives CanEqual:
  case Club
  case Player

object TournamentParticipantKind:
  given ReadWriter[TournamentParticipantKind] =
    readwriter[String].bimap(_.toString, TournamentParticipantKind.valueOf)
