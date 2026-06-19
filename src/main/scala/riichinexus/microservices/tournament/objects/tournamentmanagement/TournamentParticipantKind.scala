package riichinexus.microservices.tournament.objects.tournamentmanagement

import upickle.default.{ReadWriter, readwriter}

/** TournamentParticipantKind 枚举赛事参赛方类型 可使用的公开取值。 */

enum TournamentParticipantKind:
  case Club
  case Player

object TournamentParticipantKind:
  given ReadWriter[TournamentParticipantKind] =
    readwriter[String].bimap(_.toString, TournamentParticipantKind.valueOf)
