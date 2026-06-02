package riichinexus.microservices.tournament.objects.tournamentmanagement

import upickle.default.*

enum TournamentParticipantKind:
  case Club
  case Player

object TournamentParticipantKind:
  given ReadWriter[TournamentParticipantKind] =
    readwriter[String].bimap(_.toString, TournamentParticipantKind.valueOf)
