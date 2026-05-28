package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{TournamentStatus as DomainTournamentStatus}
import upickle.default.*

enum TournamentStatus derives CanEqual:
  case Draft
  case RegistrationOpen
  case Scheduled
  case InProgress
  case Completed
  case Cancelled
  case Archived

  def toDomain: DomainTournamentStatus =
    DomainTournamentStatus.valueOf(toString)

object TournamentStatus:
  given ReadWriter[TournamentStatus] = readwriter[String].bimap(_.toString, TournamentStatus.valueOf)

  def fromDomain(status: DomainTournamentStatus): TournamentStatus =
    TournamentStatus.valueOf(status.toString)
