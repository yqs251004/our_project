package riichinexus.microservices.tournament.objects.tournamentmanagement

import upickle.default.{ReadWriter, readwriter}

/** TournamentStatus 枚举赛事状态 可使用的公开取值。 */

enum TournamentStatus:
  case Draft
  case RegistrationOpen
  case Scheduled
  case InProgress
  case Completed
  case Cancelled
  case Archived

object TournamentStatus:
  given ReadWriter[TournamentStatus] = readwriter[String].bimap(_.toString, TournamentStatus.valueOf)
